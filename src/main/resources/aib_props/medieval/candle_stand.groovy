// tall wrought-iron candle stand with a thick lit candle, in the middle of its block; it lights the room
def z = -0.5
def iron = '#2a2826'
for (int i = 0; i < 3; i++) {
    def a = Math.toRadians(90 + i * 120)
    ai.addCapsule(iron, 0, 0.18, z, 0.2 * Math.cos(a), 0.0, z + 0.2 * Math.sin(a), 0.018)   // three curled feet
    ai.addSphere(iron, 0.21 * Math.cos(a), 0.02, z + 0.21 * Math.sin(a), 0.025)
}
ai.addCylinder(iron, 0, 0.16, z, 0.02, 1.0)                    // shaft
ai.addSphere(iron, 0, 0.6, z, 0.035)                           // knop
ai.addCone(iron, 0, 1.12, z, 0.03, 0.09, 0.05)                 // drip pan
ai.addCylinder('#efe6cc', 0, 1.17, z, 0.04, 0.22)              // thick candle
ai.addEllipsoid('#efe6cc', 0.03, 1.3, z - 0.035, 0.012, 0.04, 0.012)   // a wax drip
ai.addSphere('#ffc04a', 0, 1.42, z, 0.022)                     // flame
ai.addSphere('#fff0b0', 0, 1.425, z, 0.011)
