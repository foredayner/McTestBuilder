// desktop computer: monitor on a stand, keyboard and mouse; stands on a desk top (y = 0 is the desk's top)
def mz = -0.18
ai.addRoundBox('#2b2b2b', -0.13, 0, mz - 0.1, 0.26, 0.015, 0.16, 0.01)         // stand foot
ai.addPart('#2b2b2b', -0.03, 0.01, mz - 0.03, 0.06, 0.2, 0.03)                 // stand neck
ai.addPart('#1a1a1a', -0.31, 0.12, mz - 0.05, 0.62, 0.38, 0.035)               // monitor body
ai.addPart('#203040', -0.29, 0.14, mz - 0.052, 0.58, 0.34, 0.005)              // screen
ai.addPart('#3d6b94', -0.29, 0.14, mz - 0.054, 0.58, 0.02, 0.003)              // a taskbar
ai.addPart('#5a8fc0', -0.27, 0.36, mz - 0.054, 0.16, 0.1, 0.003)               // a window on the screen
ai.addPart('#e8e8e8', -0.07, 0.24, mz - 0.054, 0.3, 0.16, 0.003)               // another window
ai.addPart('#2b2b2b', -0.2, 0.18, mz - 0.01, 0.4, 0.26, 0.05)                  // back housing
// keyboard with rows of keys
def kz = -0.5
ai.addRoundBox('#2f2f2f', -0.23, 0, kz, 0.46, 0.025, 0.15, 0.008)
for (int r = 0; r < 4; r++) {
    ai.addPart('#4a4a4a', -0.215, 0.025, kz + 0.012 + r * 0.033, 0.43, 0.01, 0.026)
}
ai.addPart('#4a4a4a', -0.1, 0.025, kz + 0.005, 0.2, 0.01, 0.006)  // space bar edge
// mouse to the right
ai.addEllipsoid('#2f2f2f', 0.33, 0.015, kz + 0.07, 0.035, 0.02, 0.055)
