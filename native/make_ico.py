import struct, sys
png = open(sys.argv[1], 'rb').read()
header = struct.pack('<HHH', 0, 1, 1)
entry = struct.pack('<BBBBHHII', 48, 48, 0, 0, 1, 32, len(png), 22)
open(sys.argv[2], 'wb').write(header + entry + png)
