// 좌식 상: a low folding table for sitting on the floor, with a cup of barley tea and a fruit plate, in the middle of its block
def z = -0.5
def wood = '#b07a4a'
ai.addRoundBox(wood, -0.42, 0.28, z - 0.3, 0.84, 0.04, 0.6, 0.02)      // top
ai.addPart('#8a5a32', -0.4, 0.25, z - 0.28, 0.8, 0.03, 0.56)           // frame under it
ai.mirrorOn()
ai.addPart('#8a5a32', 0.32, 0, z - 0.26, 0.05, 0.26, 0.05)             // folding legs
ai.addPart('#8a5a32', 0.32, 0, z + 0.21, 0.05, 0.26, 0.05)
ai.mirrorOff()
ai.addCylinder('#e8e2d2', -0.18, 0.32, z - 0.05, 0.04, 0.07)           // cup
ai.addCylinder('#b8883a', -0.18, 0.38, z - 0.05, 0.034, 0.004)         // barley tea
ai.addCylinder('#f4f1ea', 0.12, 0.32, z + 0.02, 0.12, 0.012)           // plate
ai.addSphere('#e0602a', 0.08, 0.37, z, 0.04)                           // tangerines
ai.addSphere('#e0602a', 0.16, 0.37, z + 0.04, 0.04)
ai.addSphere('#e0702a', 0.13, 0.36, z - 0.05, 0.035)
ai.addPart('#c8cacc', 0.24, 0.322, z + 0.08, 0.08, 0.004, 0.012, 30, 0, 0)   // a fruit fork
