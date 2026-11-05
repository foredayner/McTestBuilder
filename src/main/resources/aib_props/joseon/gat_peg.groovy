// 갓걸이: a wooden wall peg with a black horsehair gat resting on it, about 1.6 up; the wall is at z = 0
def wood = '#5a3a22'
def black = '#1a1a1a'
def y = 1.55
ai.addRoundBox(wood, -0.18, y - 0.04, -0.03, 0.36, 0.08, 0.03, 0.015)   // the board on the wall
ai.addCapsule(wood, 0, y, -0.03, 0, y + 0.02, -0.2, 0.018)               // the peg
// the gat: a wide thin brim and a tall round crown of black horsehair
ai.addCylinder('#1c1c1c', 0, y + 0.03, -0.24, 0.26, 0.02)             // brim
ai.addCone('#1c1c1c', 0, y + 0.04, -0.24, 0.09, 0.085, 0.16)          // crown
ai.addCylinder(black, 0, y + 0.2, -0.24, 0.085, 0.01)                    // crown top
ai.addCylinder(black, 0, y + 0.04, -0.24, 0.092, 0.025)                  // hat band
// the chin strings of beads hanging down in front
ai.addCapsule('#d9c79a', -0.06, y + 0.03, -0.32, -0.04, y - 0.3, -0.36, 0.008)
ai.addCapsule('#d9c79a', 0.06, y + 0.03, -0.32, 0.04, y - 0.3, -0.36, 0.008)
