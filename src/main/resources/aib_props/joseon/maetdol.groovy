// 맷돌: a stone hand mill on a straw mat, in the middle of its block
def z = -0.5
def stone = '#8c8a84'
ai.addRoundBox('#c9b07a', -0.38, 0, z - 0.38, 0.76, 0.02, 0.76, 0.01)   // straw mat
ai.addCylinder('#7a7872', 0, 0.02, z, 0.32, 0.02)               // the tray lip of the lower stone
ai.addCylinder(stone, 0, 0.02, z, 0.28, 0.14)                   // lower stone
ai.addCylinder('#9a9892', 0, 0.165, z, 0.27, 0.12)              // upper stone
ai.addCylinder('#5a5852', 0.08, 0.285, z, 0.04, 0.005)          // the hole for the grain
ai.addCapsule('#6b4a2a', 0.27, 0.22, z, 0.42, 0.22, z, 0.022)   // wooden handle out the side
ai.addCylinder('#6b4a2a', 0.4, 0.22, z, 0.022, 0.16)            // and up
ai.addEllipsoid('#e6dcc0', -0.2, 0.05, z - 0.24, 0.1, 0.03, 0.06)   // a little heap of flour
