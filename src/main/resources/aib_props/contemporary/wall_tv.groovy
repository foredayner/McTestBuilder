// flat TV on the wall: 1.9 wide, 1.08 high, its middle 1.6 above the floor; the wall is at z = 0
def w = 1.9
def h = 1.08
def y = 1.06
ai.addPart('#2a2a2a', -0.25, y + 0.3, -0.03, 0.5, 0.45, 0.03)              // wall bracket
ai.addPart('#141414', -w / 2, y, -0.08, w, h, 0.05)                        // body and bezel
ai.addPart('#1e2834', -w / 2 + 0.035, y + 0.045, -0.085, w - 0.07, h - 0.08, 0.01)  // screen
ai.addPart('#2a3646', -w / 2 + 0.035, y + h * 0.55, -0.0855, w - 0.07, h * 0.4 - 0.035, 0.005)  // a soft reflection on the upper screen
ai.addPart('#8c8c8c', -0.06, y + 0.012, -0.0855, 0.12, 0.02, 0.005)        // logo
ai.addPart('#d23a2a', w / 2 - 0.1, y + 0.012, -0.0855, 0.02, 0.02, 0.005)  // standby light
