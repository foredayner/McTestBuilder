// treadle sewing machine: iron stand, wooden table, black machine head with gold lines; back at z = 0
def iron = '#262626'
def wood = '#6b3a1c'
def black = '#111111'
def gold = '#c9a24a'
def z = -0.45
ai.mirrorOn()
ai.addRoundBox(iron, 0.36, 0, z - 0.2, 0.04, 0.72, 0.4, 0.01)        // cast-iron side frames
ai.mirrorOff()
ai.addPart(iron, -0.38, 0.12, z - 0.02, 0.76, 0.03, 0.04)            // stretcher
ai.addPart(iron, -0.18, 0.06, z - 0.18, 0.36, 0.03, 0.26, 0, -12, 0)   // treadle plate
// the big wheel on the right: a disc of rows in the y-z plane
for (int j = 0; j < 10; j++) {
    def y0 = -0.2 + j * 0.04
    def ym = y0 + 0.02
    def v = 0.04 - ym * ym
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart(iron, 0.3, 0.38 + y0, z - h, 0.025, 0.042, 2 * h)
}
ai.addRoundBox(wood, -0.42, 0.72, z - 0.24, 0.84, 0.04, 0.5, 0.01)   // table top
ai.addRoundBox(black, -0.24, 0.76, z - 0.06, 0.4, 0.06, 0.16, 0.02)  // machine bed
ai.addRoundBox(black, 0.08, 0.82, z - 0.04, 0.09, 0.2, 0.1, 0.03)    // pillar
ai.addRoundBox(black, -0.22, 0.97, z - 0.04, 0.38, 0.08, 0.1, 0.035) // arm
ai.addRoundBox(black, -0.25, 0.86, z - 0.035, 0.06, 0.18, 0.09, 0.02)// head
ai.addCylinder('#c0c0c0', -0.22, 0.8, z - 0.01, 0.006, 0.06)         // needle bar
ai.addPart(gold, -0.2, 1.03, z - 0.091, 0.34, 0.006, 0.003)          // gold lines
ai.addPart(gold, -0.2, 0.99, z - 0.091, 0.34, 0.004, 0.003)
ai.addCylinder(gold, 0.14, 1.05, z - 0.02, 0.015, 0.03)              // spool pin and thread
ai.addCylinder('#b33a2a', 0.14, 1.06, z - 0.02, 0.02, 0.025)
ai.addCylinder('#c0c0c0', 0.2, 0.92, z - 0.0, 0.05, 0.02)            // hand wheel (side view)
