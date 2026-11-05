// arched wooden 'cathedral' radio with a cloth speaker and a round dial; stands on a table top (y = 0)
def z = -0.5
def wood = '#6b3a1c'
def dark = '#40200c'
ai.addRoundBox(wood, -0.2, 0, z - 0.13, 0.4, 0.28, 0.26, 0.02)            // lower cabinet
// the arch on top: rows narrowing toward the top
for (int j = 0; j < 10; j++) {
    def y0 = j * 0.02
    def v = 0.04 - (y0 + 0.01) * (y0 + 0.01)
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart(wood, -h, 0.28 + y0, z - 0.13, 2 * h, 0.022, 0.26)
}
// the speaker cloth in the arch, framed by thin wooden ribs
for (int j = 0; j < 9; j++) {
    def y0 = j * 0.02
    def v = 0.0225 - (y0 + 0.01) * (y0 + 0.01)
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart('#c8b48c', -h, 0.22 + y0, z - 0.135, 2 * h, 0.022, 0.01)
}
ai.addPart('#c8b48c', -0.15, 0.13, z - 0.135, 0.3, 0.09, 0.01)
for (int i = -2; i <= 2; i++) {
    ai.addPart(dark, i * 0.05 - 0.006, 0.13, z - 0.142, 0.012, 0.25, 0.008)   // ribs
}
ai.addPart(dark, -0.2, 0.0, z - 0.14, 0.4, 0.03, 0.02)                    // base moulding
ai.addSphere('#f2e2b0', 0, 0.075, z - 0.135, 0.03)                         // dial window
ai.addSphere(dark, -0.12, 0.07, z - 0.135, 0.025)                          // knobs
ai.addSphere(dark, 0.12, 0.07, z - 0.135, 0.025)
