# easy-nio

A Clojure library that wraps `java.nio` so you don't have to.

## Installation

```clojure
[easy-nio "0.1.0"]
```

## Namespaces

| Namespace | What it covers |
|---|---|
| `easy-nio.buffer` | ByteBuffer allocation, reading, writing |
| `easy-nio.channel` | SocketChannel, ServerSocketChannel, DatagramChannel |
| `easy-nio.selector` | Non-blocking I/O multiplexing |
| `easy-nio.file` | FileChannel, memory-mapped files, file locking |
| `easy-nio.protocols` | NIOChannel, StreamReadable, StreamWritable, DatagramIO |

---



## License

Copyright © 2026

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
