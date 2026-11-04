// standing lamp, 1.75 tall, in the middle of its block
def z = -0.5
ai.addCylinder('#2b2b2b', 0, 0, z, 0.17, 0.03)                 // heavy base
ai.addCylinder('#3a3a3a', 0, 0.03, z, 0.06, 0.03)              // collar
ai.addCylinder('#b9a37a', 0, 0.05, z, 0.022, 1.42)             // brass pole
ai.addCylinder('#2b2b2b', 0, 0.78, z, 0.03, 0.04)              // switch ring
ai.addSphere('#fff4c9', 0, 1.47, z, 0.07)                      // bulb
ai.addCone('#efe6d2', 0, 1.38, z, 0.25, 0.16, 0.34)            // shade
ai.addCone('#d9ceb6', 0, 1.37, z, 0.255, 0.245, 0.025)         // shade hem
ai.addCone('#d9ceb6', 0, 1.705, z, 0.165, 0.16, 0.02)          // shade top rim
