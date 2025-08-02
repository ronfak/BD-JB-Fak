import socket

# Create UDP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

# Bind to all interfaces (0.0.0.0) to accept connections from any IP
server_address = ('0.0.0.0', 18194)
sock.bind(server_address)

print(f"UDP server listening on all interfaces, port {server_address[1]}")

try:
    while True:
        # Receive data
        data, client_address = sock.recvfrom(1024)
        print(f"{data.decode('utf-8')}")
        
except KeyboardInterrupt:
    print("\nServer stopped")
finally:
    sock.close()