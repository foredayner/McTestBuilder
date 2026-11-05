// grandfather clock, 2.1 tall, back against the wall at z = 0, front toward -Z
def wood = '#5a3018'
def dark = '#3a1e0e'
def brass = '#c9a24a'
ai.addRoundBox(wood, -0.32, 0, -0.42, 0.64, 0.42, 0.42, 0.03)            // plinth
ai.addPart(dark, -0.33, 0.0, -0.43, 0.66, 0.06, 0.43)                    // foot moulding
ai.addRoundBox(wood, -0.26, 0.42, -0.36, 0.52, 1.08, 0.36, 0.02)         // waist
ai.addPart('#9fb8c0/25', -0.17, 0.55, -0.365, 0.34, 0.85, 0.01)          // glass door
ai.addPart(dark, -0.2, 0.52, -0.37, 0.4, 0.03, 0.02)                     // door frame
ai.addPart(dark, -0.2, 1.4, -0.37, 0.4, 0.03, 0.02)
ai.addPart(dark, -0.2, 0.52, -0.37, 0.03, 0.91, 0.02)
ai.addPart(dark, 0.17, 0.52, -0.37, 0.03, 0.91, 0.02)
ai.addPart(brass, -0.006, 0.75, -0.2, 0.012, 0.6, 0.01)                  // pendulum rod
// pendulum bob: a round disc of rows
for (int j = 0; j < 8; j++) {
    def y0 = -0.08 + j * 0.02
    def ym = y0 + 0.01
    def v = 0.0064 - ym * ym
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart(brass, -h, 0.72 + y0, -0.21, 2 * h, 0.022, 0.02)
}
ai.addRoundBox(wood, -0.34, 1.5, -0.44, 0.68, 0.58, 0.44, 0.03)          // hood
ai.addPart(dark, -0.36, 2.04, -0.46, 0.72, 0.06, 0.46)                   // crown moulding
ai.addSphere(brass, 0, 2.13, -0.25, 0.04)                                // finial
// the dial: a round face of rows, the hood's middle
def cy = 1.79
for (int j = 0; j < 14; j++) {
    def y0 = -0.21 + j * 0.03
    def ym = y0 + 0.015
    def v = 0.0441 - ym * ym
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart(brass, -h - 0.02, cy + y0, -0.445, 2 * h + 0.04, 0.032, 0.01)   // brass bezel
    def vf = 0.0361 - ym * ym
    def hf = Math.sqrt(vf < 0 ? 0.0 : vf)
    ai.addPart('#f2ead3', -hf, cy + y0, -0.452, 2 * hf, 0.032, 0.008)          // face
}
for (int i = 0; i < 12; i++) {
    def t = Math.toRadians(i * 30)
    ai.addPart('#2a2a2a', 0.15 * Math.sin(t) - 0.008, cy + 0.15 * Math.cos(t) - 0.02, -0.458, 0.016, 0.04, 0.004, 0, 0, -i * 30)
}
ai.addPart('#1a1a1a', -0.008, cy - 0.01, -0.462, 0.016, 0.11, 0.004, 0, 0, 40)   // hands
ai.addPart('#1a1a1a', -0.006, cy - 0.01, -0.464, 0.012, 0.15, 0.004, 0, 0, -70)
ai.addSphere('#1a1a1a', 0, cy, -0.466, 0.012)
