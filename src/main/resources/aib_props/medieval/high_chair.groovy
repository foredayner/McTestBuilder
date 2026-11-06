// heavy high-backed oak chair with a carved crest, facing -Z, back toward z = 0
def wood = '#4a2e1a'
def zc = -0.5
ai.mirrorOn()
ai.addPart(wood, 0.24, 0, zc - 0.28, 0.07, 0.48, 0.07)            // front legs
ai.addPart(wood, 0.24, 0, zc + 0.2, 0.07, 1.7, 0.07)              // back posts
ai.addSphere('#8a7a3a', 0.275, 1.73, zc + 0.235, 0.04)            // finials
ai.addPart(wood, 0.25, 0.68, zc - 0.28, 0.05, 0.05, 0.5)          // arms
ai.addPart(wood, 0.255, 0.48, zc - 0.27, 0.04, 0.2, 0.04)         // arm posts
ai.mirrorOff()
ai.addPart(wood, -0.31, 0.44, zc - 0.28, 0.62, 0.06, 0.55)        // seat
ai.addRoundBox('#7a2a22', -0.24, 0.5, zc - 0.25, 0.48, 0.05, 0.45, 0.02)   // red cushion
ai.addPart(wood, -0.24, 0.55, zc + 0.21, 0.48, 1.05, 0.05)        // back panel
ai.addPart('#5a3a22', -0.18, 0.7, zc + 0.205, 0.36, 0.7, 0.01)    // a carved inset
ai.addPart(wood, -0.3, 1.58, zc + 0.2, 0.6, 0.1, 0.07)            // crest rail
ai.addPart('#8a7a3a', -0.06, 1.62, zc + 0.195, 0.12, 0.12, 0.01, 0, 0, 45)   // a gold lozenge
ai.addPart(wood, -0.31, 0.08, zc - 0.25, 0.62, 0.04, 0.04)        // stretchers
ai.addPart(wood, -0.31, 0.08, zc + 0.21, 0.62, 0.04, 0.04)
