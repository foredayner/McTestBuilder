// umbrella stand with three umbrellas, back at z = 0 (by the door)
def z = -0.3
ai.addCylinder('#3a4a3a', 0, 0, z, 0.13, 0.5)                   // stand
ai.addCylinder('#2a3a2a', 0, 0.48, z, 0.135, 0.03)              // rim
ai.addCapsule('#1c1c1c', -0.05, 0.3, z, -0.08, 0.82, z + 0.03, 0.012)    // black umbrella shaft
ai.addCone('#1c1c1c', -0.07, 0.42, z + 0.02, 0.06, 0.02, 0.3)            // its furled cloth
ai.addCapsule('#6b4a2a', -0.08, 0.82, z + 0.03, -0.14, 0.86, z + 0.03, 0.012)   // crook handle
ai.addCapsule('#1c1c1c', 0.05, 0.3, z - 0.03, 0.09, 0.8, z - 0.06, 0.012)
ai.addCone('#a8322a', 0.075, 0.42, z - 0.05, 0.06, 0.02, 0.3)            // red umbrella
ai.addSphere('#6b4a2a', 0.09, 0.82, z - 0.06, 0.025)
ai.addCapsule('#1c1c1c', 0.0, 0.3, z + 0.05, 0.02, 0.76, z + 0.08, 0.012)
ai.addCone('#2e4a7a', 0.012, 0.42, z + 0.07, 0.055, 0.02, 0.28)          // blue umbrella
ai.addSphere('#c8cacc', 0.02, 0.78, z + 0.08, 0.02)
