// an unrolled map held down by a dagger and a candle stub, with rolled scrolls; on a table top (y = 0)
def z = -0.5
ai.addPart('#d8c49a', -0.3, 0, z - 0.22, 0.5, 0.006, 0.36)          // the map
ai.addCapsule('#c8b48a', -0.3, 0.02, z - 0.22, -0.3, 0.02, z + 0.14, 0.02)   // its rolled edges
ai.addCapsule('#c8b48a', 0.2, 0.02, z - 0.22, 0.2, 0.02, z + 0.14, 0.02)
ai.addPart('#5a7a9a', -0.24, 0.007, z - 0.1, 0.16, 0.002, 0.14)     // a sea
ai.addPart('#6a5a3a', -0.06, 0.007, z - 0.16, 0.002, 0.002, 0.26)  // roads
ai.addPart('#6a5a3a', -0.2, 0.007, z + 0.04, 0.34, 0.002, 0.002)
ai.addSphere('#a8322a', 0.08, 0.01, z - 0.06, 0.015)                // a red mark
ai.addPart('#a8a8a8', -0.12, 0.015, z + 0.02, 0.2, 0.006, 0.02, 30, 0, 0)    // dagger blade
ai.addPart('#5a3a22', 0.05, 0.015, z + 0.1, 0.07, 0.016, 0.025, 30, 0, 0)    // its grip
ai.addCylinder('#efe6cc', 0.15, 0, z - 0.16, 0.025, 0.06)           // candle stub
ai.addSphere('#ffc04a', 0.15, 0.08, z - 0.16, 0.012)
ai.addCapsule('#d8c49a', 0.26, 0.03, z - 0.2, 0.26, 0.03, z + 0.2, 0.028)    // rolled scrolls
ai.addCapsule('#c8b48a', 0.32, 0.03, z - 0.15, 0.3, 0.03, z + 0.18, 0.025)
ai.addCapsule('#a8322a', 0.255, 0.03, z, 0.265, 0.03, z, 0.031)              // red ribbon
