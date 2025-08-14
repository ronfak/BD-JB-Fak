/* Copyright (C) 2025 John Törnblom

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation; either version 3, or (at your option) any
later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; see the file COPYING. If not, see
<http://www.gnu.org/licenses/>.  */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <pthread.h>
#include <errno.h>
#include <stdint.h>

typedef struct notify_request {
    char useless1[45];
    char message[3075];
} notify_request_t;

int sceKernelSendNotificationRequest(int, notify_request_t*, size_t, int);

#define PAGE_SIZE 0x1000
#define MAX_PAYLOAD_SIZE (4 * 1024 * 1024)  // 4MB
#define COPY_CHUNK_SIZE 8192

#define ELF_MAGIC 0x464c457f  // 0x7F 'E' 'L' 'F' in little endian
#define PT_LOAD 1

static const char* USB_PAYLOAD_PATHS[] = {
    "/mnt/usb0/payload.bin",
    "/mnt/usb1/payload.bin", 
    "/mnt/usb2/payload.bin",
    "/mnt/usb3/payload.bin",
    "/mnt/usb4/payload.bin"
};
#define USB_PATHS_COUNT (sizeof(USB_PAYLOAD_PATHS) / sizeof(USB_PAYLOAD_PATHS[0]))

static const char* DATA_PAYLOAD_PATH = "/data/payload.bin";

typedef struct {
    uint64_t e_entry;
    uint64_t e_phoff;
    uint16_t e_phentsize;
    uint16_t e_phnum;
} elf_header_t;

typedef struct {
    uint32_t p_type;
    uint64_t p_offset;
    uint64_t p_vaddr;
    uint64_t p_filesz;
    uint64_t p_memsz;
} program_header_t;

static void* mmap_base = NULL;
static size_t mmap_size = 0;
static void* entry_point = NULL;
static pthread_t payload_thread = 0;

void send_notification(const char* message) {
    notify_request_t req;
    memset(&req, 0, sizeof(req));
    strncpy(req.message, message, sizeof(req.message) - 1);
    sceKernelSendNotificationRequest(0, &req, sizeof(req), 0);
}

size_t round_up(size_t value, size_t boundary) {
    return ((value + boundary - 1) / boundary) * boundary;
}

int file_exists(const char* path) {
    struct stat st;
    if (stat(path, &st) == 0) {
        if (S_ISREG(st.st_mode)) {
            return 1;
        }
    }
    return 0;
}

int copy_file(const char* source_path, const char* dest_path) {
    int src_fd = -1, dest_fd = -1;
    char buffer[COPY_CHUNK_SIZE];
    ssize_t bytes_read, bytes_written;
    struct stat st;
    int result = -1;

    if (stat(source_path, &st) != 0) {
        return -1;
    }

    if (!S_ISREG(st.st_mode)) {
        return -1;
    }

    if (st.st_size > MAX_PAYLOAD_SIZE) {
        return -1;
    }

    src_fd = open(source_path, O_RDONLY);
    if (src_fd < 0) {
        goto cleanup;
    }

    dest_fd = open(dest_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (dest_fd < 0) {
        goto cleanup;
    }

    size_t total_copied = 0;
    while ((bytes_read = read(src_fd, buffer, sizeof(buffer))) > 0) {
        bytes_written = write(dest_fd, buffer, bytes_read);
        if (bytes_written != bytes_read) {
            goto cleanup;
        }
        total_copied += bytes_written;

        if (total_copied > MAX_PAYLOAD_SIZE) {
            goto cleanup;
        }
    }

    if (bytes_read < 0) {
        goto cleanup;
    }

    result = 0;

cleanup:
    if (src_fd >= 0) close(src_fd);
    if (dest_fd >= 0) close(dest_fd);
    return result;
}

uint8_t* read_file(const char* file_path, size_t* size_out) {
    int fd;
    struct stat st;
    uint8_t* data = NULL;
    ssize_t bytes_read, total_read = 0;

    if (stat(file_path, &st) != 0) {
        return NULL;
    }

    if (!S_ISREG(st.st_mode)) {
        return NULL;
    }

    if (st.st_size > MAX_PAYLOAD_SIZE) {
        return NULL;
    }

    if (st.st_size == 0) {
        return NULL;
    }

    fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        return NULL;
    }

    data = malloc(st.st_size);
    if (!data) {
        close(fd);
        return NULL;
    }

    while (total_read < st.st_size) {
        bytes_read = read(fd, data + total_read, st.st_size - total_read);
        if (bytes_read <= 0) {
            free(data);
            close(fd);
            return NULL;
        }
        total_read += bytes_read;
    }

    close(fd);
    *size_out = st.st_size;
    return data;
}

void read_elf_header(void* addr, elf_header_t* header) {
    uint8_t* ptr = (uint8_t*)addr;
    header->e_entry = *(uint64_t*)(ptr + 0x18);
    header->e_phoff = *(uint64_t*)(ptr + 0x20);
    header->e_phentsize = *(uint16_t*)(ptr + 0x36);
    header->e_phnum = *(uint16_t*)(ptr + 0x38);
}

void read_program_header(void* addr, program_header_t* phdr) {
    uint8_t* ptr = (uint8_t*)addr;
    phdr->p_type = *(uint32_t*)(ptr + 0x00);
    phdr->p_offset = *(uint64_t*)(ptr + 0x08);
    phdr->p_vaddr = *(uint64_t*)(ptr + 0x10);
    phdr->p_filesz = *(uint64_t*)(ptr + 0x20);
    phdr->p_memsz = *(uint64_t*)(ptr + 0x28);
}

void* load_elf_segments(uint8_t* data, size_t data_size) {
    void* temp_buf = mmap(NULL, data_size, PROT_READ | PROT_WRITE, 
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (temp_buf == MAP_FAILED) {
        return NULL;
    }

    memcpy(temp_buf, data, data_size);

    elf_header_t elf_header;
    read_elf_header(temp_buf, &elf_header);

    for (int i = 0; i < elf_header.e_phnum; i++) {
        void* phdr_addr = (uint8_t*)temp_buf + elf_header.e_phoff + (i * elf_header.e_phentsize);
        program_header_t phdr;
        read_program_header(phdr_addr, &phdr);

        if (phdr.p_type == PT_LOAD && phdr.p_memsz > 0) {
            void* seg_addr = (uint8_t*)mmap_base + (phdr.p_vaddr % 0x1000000);

            if (phdr.p_filesz > 0) {
                memcpy(seg_addr, data + phdr.p_offset, phdr.p_filesz);
            }

            if (phdr.p_memsz > phdr.p_filesz) {
                memset((uint8_t*)seg_addr + phdr.p_filesz, 0, phdr.p_memsz - phdr.p_filesz);
            }
        }
    }

    void* entry = (uint8_t*)mmap_base + (elf_header.e_entry % 0x1000000);

    munmap(temp_buf, data_size);

    return entry;
}

int load_from_data(uint8_t* data, size_t data_size) {
    if (!data || data_size == 0) {
        return -1;
    }

    if (data_size > MAX_PAYLOAD_SIZE) {
        return -1;
    }

    mmap_size = round_up(data_size, PAGE_SIZE);

    mmap_base = mmap(NULL, mmap_size, PROT_READ | PROT_WRITE | PROT_EXEC,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mmap_base == MAP_FAILED) {
        return -1;
    }

    if (data_size >= 4) {
        uint32_t magic = *(uint32_t*)data;
        if (magic == ELF_MAGIC) {
            entry_point = load_elf_segments(data, data_size);
        } else {
            memcpy(mmap_base, data, data_size);
            entry_point = mmap_base;
        }
    } else {
        munmap(mmap_base, mmap_size);
        mmap_base = NULL;
        return -1;
    }

    if (!entry_point) {
        munmap(mmap_base, mmap_size);
        mmap_base = NULL;
        return -1;
    }

    return 0;
}

void* payload_thread_func(void* arg) {
    int (*payload_func)(void) = (int (*)(void))entry_point;
    payload_func();
    return NULL;
}

int run_payload() {
    if (pthread_create(&payload_thread, NULL, payload_thread_func, NULL) != 0) {
        return -1;
    }
    return 0;
}

void wait_for_payload_to_exit() {
    if (payload_thread != 0) {
        pthread_join(payload_thread, NULL);
        payload_thread = 0;
    }

    if (mmap_base && mmap_size > 0) {
        munmap(mmap_base, mmap_size);
        mmap_base = NULL;
        mmap_size = 0;
        entry_point = NULL;
    }
}

void execute_payload_from_path(const char* payload_path) {
    size_t data_size;
    uint8_t* data;

    if (!file_exists(payload_path)) {
        return;
    }

    data = read_file(payload_path, &data_size);
    if (!data) {
        return;
    }

    if (load_from_data(data, data_size) == 0) {
        if (run_payload() == 0) {
            wait_for_payload_to_exit();
        }
    }

    free(data);
}

int main() {
    // Priority 1: Check for USB payload on usb0-usb4
    for (int i = 0; i < USB_PATHS_COUNT; i++) {
        const char* usb_path = USB_PAYLOAD_PATHS[i];
        if (file_exists(usb_path)) {
            send_notification("USB payload.bin found - executing...");
            
            if (copy_file(usb_path, DATA_PAYLOAD_PATH) == 0) {
                send_notification("USB payload copied to /data/payload.bin");
            }

            // Execute from USB location
            execute_payload_from_path(usb_path);
            return 0;
        }
    }

    // Priority 2: Check for existing payload in data directory
    if (file_exists(DATA_PAYLOAD_PATH)) {
        send_notification("/data/payload.bin found - executing...");
        execute_payload_from_path(DATA_PAYLOAD_PATH);
        return 0;
    }
	
    return 0;
}