// 등잔: a tall wooden lamp stand with an oil dish and a small flame, in the middle of its block; it lights the room
def z = -0.5
def wood = '#5a3a22'
ai.addCylinder(wood, 0, 0, z, 0.16, 0.04)                       // round foot
ai.addCone(wood, 0, 0.04, z, 0.12, 0.05, 0.05)                  // its shoulder
ai.addCylinder(wood, 0, 0.09, z, 0.022, 0.62)                   // the pole
ai.addCylinder(wood, 0, 0.36, z, 0.06, 0.02)                    // a little shelf halfway
ai.addCylinder(wood, 0, 0.71, z, 0.08, 0.02)                    // dish holder
ai.addCone('#d8cfb8', 0, 0.73, z, 0.04, 0.075, 0.035)           // white porcelain oil dish
ai.addCylinder('#6a5a3a', 0, 0.755, z, 0.06, 0.008)             // the oil in it
ai.addCapsule('#e8dcc0', 0.02, 0.765, z, 0.05, 0.79, z, 0.004)  // wick
ai.addSphere('#ffc04a', 0.052, 0.805, z, 0.016)                 // flame
ai.addSphere('#fff0b0', 0.052, 0.81, z, 0.008)
