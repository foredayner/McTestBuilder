// woven tapestry with a hunting scene, 2 wide, hung from a pole on the wall (z = 0)
def y = 0.4
def wood = '#5a3a22'
ai.addCapsule(wood, -0.98, y + 1.72, -0.05, 0.98, y + 1.72, -0.05, 0.02)     // pole
ai.mirrorOn()
ai.addSphere('#8a7a3a', 0.99, y + 1.72, -0.05, 0.03)                        // its ends
ai.mirrorOff()
ai.addPart('#7a2a22', -0.9, y, -0.04, 1.8, 1.7, 0.012)                      // red wool
ai.addPart('#c8a04a', -0.9, y + 1.62, -0.046, 1.8, 0.04, 0.006)            // gold borders
ai.addPart('#c8a04a', -0.9, y + 0.06, -0.046, 1.8, 0.04, 0.006)
ai.addPart('#c8a04a', -0.86, y, -0.046, 0.04, 1.7, 0.006)
ai.addPart('#c8a04a', 0.82, y, -0.046, 0.04, 1.7, 0.006)
ai.addPart('#3a5a2a', -0.78, y + 0.14, -0.048, 1.56, 0.4, 0.004)           // green field
ai.addPart('#4a3a2a', -0.5, y + 0.54, -0.05, 0.08, 0.5, 0.004)             // a tree
ai.addEllipsoid('#2f4a2a', -0.46, y + 1.12, -0.052, 0.2, 0.2, 0.006)
ai.addPart('#e8e0c8', 0.0, y + 0.6, -0.05, 0.36, 0.16, 0.004)              // a white stag
ai.addPart('#e8e0c8', 0.02, y + 0.42, -0.05, 0.03, 0.2, 0.004)             // its legs
ai.addPart('#e8e0c8', 0.3, y + 0.42, -0.05, 0.03, 0.2, 0.004)
ai.addPart('#e8e0c8', 0.32, y + 0.74, -0.05, 0.08, 0.18, 0.004)            // neck and head
ai.addPart('#c8a04a', 0.34, y + 0.92, -0.052, 0.02, 0.14, 0.004, 0, 0, 25) // antlers
ai.addPart('#c8a04a', 0.4, y + 0.92, -0.052, 0.02, 0.14, 0.004, 0, 0, -25)
ai.addPart('#2a2a4a', 0.55, y + 0.6, -0.05, 0.08, 0.36, 0.004)             // a hunter
ai.addSphere('#e8c8a0', 0.59, y + 1.02, -0.052, 0.05)
ai.addPart('#5a3a22', 0.45, y + 0.8, -0.052, 0.2, 0.012, 0.004, 0, 0, 10)  // his spear
