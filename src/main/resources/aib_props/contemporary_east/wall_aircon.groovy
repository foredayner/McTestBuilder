// 벽걸이 에어컨: a long white wall air conditioner high on the wall (z = 0), 2 wide
def white = '#f2f0ea'
def y = 2.1
ai.addRoundBox(white, -0.95, y, -0.26, 1.9, 0.32, 0.26, 0.06)       // body
ai.addPart('#d8d4cc', -0.92, y + 0.02, -0.265, 1.84, 0.08, 0.01)    // the louvre slot
ai.addPart('#c8c4bc', -0.9, y + 0.05, -0.27, 1.8, 0.02, 0.006, 0, 0, 0)   // louvre blade
ai.addPart('#5a8ab8', 0.55, y + 0.2, -0.265, 0.2, 0.04, 0.004)      // blue display
ai.addSphere('#3ac06a', 0.8, y + 0.22, -0.266, 0.008)               // power light
ai.addPart('#c8c4bc', -0.8, y + 0.26, -0.262, 1.2, 0.006, 0.003)    // a thin line across
