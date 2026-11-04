// three-seat sofa, 2.9 wide, 0.95 deep; back against z = 0, front toward -Z
def w = 2.9
def d = 0.95
def fabric = '#7d8590'
def light = '#8f97a2'
def dark = '#5f666f'
ai.addRoundBox(fabric, -w / 2, 0.1, -d, w, 0.34, d, 0.05)                 // seat base
ai.addRoundBox(fabric, -w / 2, 0.42, -0.26, w, 0.5, 0.26, 0.08)           // back
ai.addRoundBox(dark, -w / 2 + 0.02, 0.1, -d + 0.02, w - 0.04, 0.04, d - 0.04, 0.02)  // shadow line at the bottom
ai.mirrorOn()
ai.addRoundBox(fabric, w / 2 - 0.26, 0.1, -d, 0.26, 0.58, d, 0.09)        // arms
ai.addCylinder('#3b2f26', w / 2 - 0.12, 0, -d + 0.1, 0.035, 0.1)          // legs
ai.addCylinder('#3b2f26', w / 2 - 0.12, 0, -0.1, 0.035, 0.1)
ai.mirrorOff()
def inner = w - 0.52
def cw = inner / 3
for (int i = 0; i < 3; i++) {
    def x = -inner / 2 + i * cw
    ai.addRoundBox(light, x + 0.01, 0.42, -d + 0.03, cw - 0.02, 0.14, d - 0.3, 0.05)   // seat cushions
    ai.addRoundBox(light, x + 0.02, 0.56, -0.42, cw - 0.04, 0.33, 0.18, 0.07)          // back cushions
}
ai.addRoundBox('#c9b38a', -inner / 2 + 0.06, 0.55, -0.5, 0.34, 0.3, 0.12, 0.06)   // a throw pillow on the left
