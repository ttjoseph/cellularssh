# CellularSSH #

This is an SSH client for BlackBerry that I wrote some time back because I couldn't find one I liked enough (_e.g._ MidpSSH was pretty clunky). It mostly works, but is still missing some features, such as support for a Ctrl key, a scrollback buffer, and decent VT100 emulation. But it can be useful in a pinch.

It uses the [Bouncy Castle](http://www.bouncycastle.org) crypto library, so it doesn't depend on the crypto libraries supplied by RIM. Hooray for Bouncy Castle. Despite this, I make no guarantees that CellularSSH is secure, because there may be undiscovered bugs. If you are trying to keep the NSA out of your internets, you should probably consider using something else. Use CellularSSH at your own risk.

CellularSSH has been tested on 83xx, 88xx and 9000 devices. It only uses cell service; it won't even try to use Wi-Fi. Visit [http://qux.us/cellularssh](http://qux.us/cellularssh) using your BlackBerry to install.