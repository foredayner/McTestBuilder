// three-branch brass candelabra with lit candles; stands on a table top (y = 0 is the top); it lights the room
def z = -0.5
def brass = '#c9a24a'
ai.addCylinder(brass, 0, 0, z, 0.08, 0.02)                         // foot
ai.addCone(brass, 0, 0.02, z, 0.06, 0.02, 0.05)
ai.addCylinder(brass, 0, 0.07, z, 0.014, 0.2)                      // stem
ai.addSphere(brass, 0, 0.16, z, 0.022)                             // knop
ai.mirrorOn()
ai.addCapsule(brass, 0.0, 0.26, z, 0.08, 0.3, z, 0.01)             // branches
ai.addCapsule(brass, 0.08, 0.3, z, 0.12, 0.34, z, 0.01)
ai.addCylinder(brass, 0.12, 0.34, z, 0.025, 0.015)                 // cups
ai.addCylinder('#f4efe0', 0.12, 0.355, z, 0.014, 0.14)             // candles
ai.addSphere('#ffcf5a', 0.12, 0.515, z, 0.012)                     // flames
ai.mirrorOff()
ai.addCylinder(brass, 0, 0.27, z, 0.025, 0.015)                    // middle cup, higher
ai.addCylinder('#f4efe0', 0, 0.285, z, 0.014, 0.18)
ai.addSphere('#ffcf5a', 0, 0.485, z, 0.012)
