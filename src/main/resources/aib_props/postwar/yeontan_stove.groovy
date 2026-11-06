// 연탄난로: a cast-iron coal-briquette stove with a chimney pipe and a spare briquette, in the middle of its block; it gives warmth and light
def z = -0.5
def iron = '#2e2c2a'
ai.addCylinder(iron, 0, 0.06, z, 0.2, 0.5)                       // body
ai.mirrorOn()
ai.addCylinder(iron, 0.14, 0, z - 0.14, 0.03, 0.06)              // feet
ai.addCylinder(iron, 0.14, 0, z + 0.14, 0.03, 0.06)
ai.mirrorOff()
ai.addCylinder('#3a3836', 0, 0.56, z, 0.22, 0.04)                // top plate
ai.addCylinder('#c8cacc', 0.1, 0.6, z - 0.06, 0.08, 0.1)         // a pot warming on it
ai.addRoundBox('#4a4846', -0.08, 0.16, z - 0.205, 0.16, 0.12, 0.01, 0.01)   // ash door
ai.addPart('#ff7a2a', -0.06, 0.18, z - 0.21, 0.12, 0.03, 0.004)  // glow through its slit
ai.addCylinder('#5a5856', -0.08, 0.6, z + 0.08, 0.05, 0.3)       // chimney pipe up
ai.addCapsule('#5a5856', -0.08, 0.9, z + 0.08, -0.08, 0.98, z + 0.48, 0.05)   // then back to the wall
// a spare briquette: a black cylinder with holes
ai.addCylinder('#1a1a1a', 0.3, 0, z + 0.24, 0.075, 0.07)
for (int i = 0; i < 5; i++) {
    def a = Math.toRadians(i * 72)
    ai.addCylinder('#4a4846', 0.3 + 0.035 * Math.cos(a), 0.071, z + 0.24 + 0.035 * Math.sin(a), 0.01, 0.002)
}
