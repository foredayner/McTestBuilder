// candlestick telephone with its earpiece on the hook; stands on a table top (y = 0 is the top)
def z = -0.5
def black = '#151515'
def nickel = '#b8b8b8'
ai.addCylinder(black, 0, 0, z, 0.075, 0.03)                         // round foot
ai.addCone(black, 0, 0.03, z, 0.06, 0.02, 0.05)                    // its shoulder
ai.addCylinder(black, 0, 0.08, z, 0.016, 0.26)                     // the stem
ai.addCylinder(nickel, 0, 0.1, z, 0.019, 0.01)                     // nickel bands
ai.addCylinder(nickel, 0, 0.3, z, 0.019, 0.01)
ai.addSphere(black, 0, 0.35, z, 0.025)                             // head
ai.addCapsule(black, 0, 0.36, z, 0, 0.36, z - 0.06, 0.012)         // neck to the mouthpiece
ai.addCone(black, 0, 0.33, z - 0.07, 0.015, 0.04, 0.06)            // mouthpiece, opening upward
ai.addCapsule(nickel, 0.02, 0.29, z, 0.06, 0.29, z, 0.006)         // the hook
ai.addCapsule(black, 0.075, 0.18, z, 0.075, 0.3, z, 0.022)         // earpiece hanging from it
ai.addCone(black, 0.075, 0.14, z, 0.035, 0.022, 0.04)              // its cup
ai.addTube(black, [[0, 0.02, z + 0.07], [0.06, 0.01, z + 0.1], [0.12, 0.0, z + 0.08]], 0.005)   // cord
