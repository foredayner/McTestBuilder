// 옛날 선풍기: a mint-green electric fan with a round guard and piano-key buttons, on the floor in the middle of its block
def z = -0.5
def mint = '#9ac8b0'
ai.addRoundBox(mint, -0.15, 0, z - 0.12, 0.3, 0.06, 0.24, 0.04)       // base
for (int i = 0; i < 4; i++) {
    ai.addRoundBox(i == 0 ? '#e8e2d2' : '#d8d2c2', -0.12 + i * 0.06, 0.06, z - 0.13, 0.05, 0.02, 0.05, 0.01)   // piano buttons
}
ai.addCylinder(mint, 0, 0.06, z + 0.02, 0.025, 0.55)                  // stem
ai.addEllipsoid(mint, 0, 0.66, z + 0.06, 0.07, 0.07, 0.1)             // motor
// the guard: a ring of wire facing front
for (int i = 0; i < 16; i++) {
    def a0 = Math.toRadians(i * 22.5)
    def a1 = Math.toRadians((i + 1) * 22.5)
    ai.addCapsule('#d8dadc', 0.2 * Math.cos(a0), 0.66 + 0.2 * Math.sin(a0), z - 0.06, 0.2 * Math.cos(a1), 0.66 + 0.2 * Math.sin(a1), z - 0.06, 0.006)
}
for (int i = 0; i < 4; i++) {                                         // the blades
    def a = Math.toRadians(i * 90 + 20)
    ai.addEllipsoid('#7ab8a0', 0.1 * Math.cos(a), 0.66 + 0.1 * Math.sin(a), z - 0.04, 0.06, 0.06, 0.01)
}
ai.addSphere('#e8e2d2', 0, 0.66, z - 0.065, 0.03)                     // center badge
