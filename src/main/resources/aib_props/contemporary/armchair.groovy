// one-seat armchair, 0.95 wide and deep
def w = 0.95
def d = 0.95
def fabric = '#8b7563'
def light = '#9c8674'
def dark = '#6c5848'
ai.addRoundBox(fabric, -w / 2, 0.1, -d, w, 0.34, d, 0.05)                 // seat base
ai.addRoundBox(fabric, -w / 2, 0.42, -0.26, w, 0.52, 0.26, 0.08)          // back
ai.addRoundBox(dark, -w / 2 + 0.02, 0.1, -d + 0.02, w - 0.04, 0.04, d - 0.04, 0.02)
ai.mirrorOn()
ai.addRoundBox(fabric, w / 2 - 0.2, 0.1, -d, 0.2, 0.56, d, 0.08)          // arms
ai.addCylinder('#3b2f26', w / 2 - 0.1, 0, -d + 0.1, 0.03, 0.1)            // legs
ai.addCylinder('#3b2f26', w / 2 - 0.1, 0, -0.1, 0.03, 0.1)
ai.mirrorOff()
ai.addRoundBox(light, -w / 2 + 0.21, 0.42, -d + 0.03, w - 0.42, 0.14, d - 0.3, 0.05)  // seat cushion
ai.addRoundBox(light, -w / 2 + 0.22, 0.56, -0.42, w - 0.44, 0.34, 0.18, 0.07)         // back cushion
