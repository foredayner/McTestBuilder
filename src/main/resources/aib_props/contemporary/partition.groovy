// office partition, 1.96 wide and 1.75 high, sky-blue fabric panel in an aluminium frame, on two feet
def w = 1.96
def h = 1.75
def frame = '#c9ced3'
def panel = '#9fd3f0'
def z = -0.5
ai.addPart(panel, -w / 2 + 0.04, 0.08, z - 0.03, w - 0.08, h - 0.12, 0.06)           // fabric panel
ai.addPart('#8cc3e3', -w / 2 + 0.04, 0.08, z - 0.034, w - 0.08, 0.03, 0.068)         // darker lower edge
ai.addPart(frame, -w / 2 + 0.04, h - 0.04, z - 0.04, w - 0.08, 0.04, 0.08)           // top rail
ai.addPart(frame, -w / 2 + 0.04, 0.05, z - 0.04, w - 0.08, 0.03, 0.08)               // bottom rail
ai.mirrorOn()
ai.addPart(frame, w / 2 - 0.04, 0, z - 0.04, 0.04, h, 0.08)                         // side posts
ai.addRoundBox('#9aa1a8', w / 2 - 0.1, 0, z - 0.2, 0.08, 0.04, 0.4, 0.015)           // feet
ai.mirrorOff()
