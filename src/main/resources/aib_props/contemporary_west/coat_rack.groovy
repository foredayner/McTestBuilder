// standing coat rack with a coat, a scarf and a hat, in the middle of its block
def z = -0.5
def wood = '#4a2e1a'
for (int i = 0; i < 4; i++) {
    def a = Math.toRadians(45 + i * 90)
    ai.addCapsule(wood, 0, 0.12, z, 0.22 * Math.cos(a), 0.02, z + 0.22 * Math.sin(a), 0.02)   // legs
}
ai.addCylinder(wood, 0, 0.1, z, 0.025, 1.65)                    // post
ai.addSphere(wood, 0, 1.77, z, 0.04)                            // knob
for (int i = 0; i < 4; i++) {
    def a = Math.toRadians(i * 90)
    ai.addCapsule(wood, 0, 1.6, z, 0.13 * Math.cos(a), 1.68, z + 0.13 * Math.sin(a), 0.012)   // hooks
}
ai.addRoundBox('#2e3a52', -0.18, 0.75, z - 0.2, 0.3, 0.86, 0.12, 0.05)   // navy coat hanging in front
ai.addRoundBox('#2e3a52', -0.2, 1.48, z - 0.2, 0.34, 0.14, 0.14, 0.06)   // its shoulders
ai.addRoundBox('#a8322a', 0.08, 1.0, z + 0.06, 0.08, 0.62, 0.04, 0.02)   // red scarf
ai.addCylinder('#3a3a3a', 0.1, 1.66, z + 0.08, 0.12, 0.012)               // hat brim
ai.addCylinder('#3a3a3a', 0.1, 1.67, z + 0.08, 0.07, 0.09)                // hat crown
