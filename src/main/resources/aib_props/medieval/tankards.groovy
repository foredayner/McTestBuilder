// two wooden tankards of ale and a loaf of bread; stand on a table top (y = 0)
def z = -0.5
def wood = '#7a5432'
def band = '#3a3836'
ai.mirrorOn()
ai.addCylinder(wood, 0.13, 0, z - 0.05, 0.06, 0.16)              // tankards
ai.addCylinder(band, 0.13, 0.03, z - 0.05, 0.062, 0.012)         // iron hoops
ai.addCylinder(band, 0.13, 0.12, z - 0.05, 0.062, 0.012)
ai.addEllipsoid('#f2ead0', 0.13, 0.16, z - 0.05, 0.058, 0.02, 0.058)   // the foam
ai.addCapsule(wood, 0.19, 0.04, z - 0.05, 0.22, 0.08, z - 0.05, 0.014) // handles
ai.addCapsule(wood, 0.22, 0.08, z - 0.05, 0.19, 0.13, z - 0.05, 0.014)
ai.mirrorOff()
ai.addEllipsoid('#b8803a', 0, 0.05, z + 0.12, 0.13, 0.06, 0.08)      // loaf
ai.addPart('#8a5a2a', -0.06, 0.1, z + 0.08, 0.012, 0.01, 0.08, 20, 0, 0)  // its slashes
ai.addPart('#8a5a2a', 0.04, 0.1, z + 0.08, 0.012, 0.01, 0.08, 20, 0, 0)
