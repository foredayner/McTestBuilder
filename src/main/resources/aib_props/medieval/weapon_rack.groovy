// wooden weapon rack with two swords, a spear and an axe, back against the wall at z = 0
def wood = '#5a3a22'
def steel = '#b8bcc0'
ai.mirrorOn()
ai.addPart(wood, 0.38, 0, -0.3, 0.06, 1.5, 0.06)                   // uprights
ai.mirrorOff()
ai.addPart(wood, -0.44, 0.08, -0.4, 0.88, 0.05, 0.25)              // bottom rack
ai.addPart(wood, -0.44, 1.2, -0.3, 0.88, 0.06, 0.06)               // top bar
ai.addPart(wood, -0.44, 0.6, -0.3, 0.88, 0.05, 0.05)               // middle bar
// two swords standing point down
ai.mirrorOn()
ai.addPart(steel, 0.22, 0.13, -0.32, 0.04, 0.95, 0.008)
ai.addPart('#8a7a3a', 0.16, 1.08, -0.34, 0.16, 0.03, 0.04)        // crossguards
ai.addPart('#4a2e1a', 0.225, 1.11, -0.33, 0.03, 0.16, 0.03)       // grips
ai.addSphere('#8a7a3a', 0.24, 1.29, -0.315, 0.025)                // pommels
ai.mirrorOff()
ai.addCylinder(wood, 0, 0.13, -0.24, 0.016, 1.3)                   // spear in the middle
ai.addCone(steel, 0, 1.43, -0.24, 0.03, 0.0, 0.14)
ai.addCapsule(wood, -0.08, 0.13, -0.18, -0.08, 0.9, -0.18, 0.018)  // axe haft
ai.addPart(steel, -0.2, 0.74, -0.185, 0.12, 0.13, 0.012)           // axe head
