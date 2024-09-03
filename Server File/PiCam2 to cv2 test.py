import cv2

from picamera2 import Picamera2

# replace cv2.VideoStream()
cam2 = Picamera2()
cam2.configure(cam2.create_preview_configuration(main={"format": 'XRGB8888', "size": (640, 480)}))
cam2.start()

while True:
    # replaces cv2.VideoStream().read()
    # same, returns numpy array
    img = cam2.capture_array()
    cv2.imshow("Camera Test", img)
    cv2.waitKey(1)
