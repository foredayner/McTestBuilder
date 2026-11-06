// framed landscape picture on the wall (z = 0), about 1.6 up, 1 wide
def y = 1.25
ai.addRoundBox('#2a2a2a', -0.42, y, -0.04, 0.84, 0.62, 0.04, 0.01)      // black frame
ai.addPart('#f4f1ea', -0.36, y + 0.06, -0.045, 0.72, 0.5, 0.01)          // white mount
ai.addPart('#8fb8d8', -0.3, y + 0.32, -0.05, 0.6, 0.18, 0.006)           // sky
ai.addPart('#5f8a4a', -0.3, y + 0.12, -0.05, 0.6, 0.2, 0.006)            // fields
ai.addPart('#4a6a8a', -0.3, y + 0.24, -0.052, 0.36, 0.1, 0.006, 0, 0, 8) // hills
ai.addPart('#3a5a3a', -0.05, y + 0.22, -0.052, 0.35, 0.1, 0.006, 0, 0, -6)
ai.addSphere('#f2d26a', 0.18, y + 0.42, -0.054, 0.035)                   // sun
ai.addPart('#5a3a22', 0.1, y + 0.16, -0.054, 0.02, 0.1, 0.004)           // a little tree
ai.addSphere('#2f5f2f', 0.11, y + 0.27, -0.056, 0.04)
