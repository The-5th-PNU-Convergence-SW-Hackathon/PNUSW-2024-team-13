import tensorflow as tf
import tensorflow_hub as hub
import cv2
import numpy as np
from picamera2 import Picamera2
from flask import Flask, Response, render_template

# Download the model from TF Hub.
# model = tf.saved_model.load("posenet_mobilenet_v1_100_257x257_multi_kpt_stripped.tflite")
model = hub.load('https://tfhub.dev/google/movenet/singlepose/thunder/3')
movenet = model.signatures['serving_default']

# Load Video
video_source = 0
cap = Picamera2()
cap.configure(cap.create_preview_configuration(main={"format": 'XRGB8888', "size": (1640, 1232)}))
cap.start()

# Flask app generate
app = Flask(__name__)

frame_rate_calc = 1
freq = cv2.getTickFrequency()

def calc_Ske(original = True):
    while True:
        # Start timer (for calculating frame rate)
        # t1 = cv2.getTickCount()
        
        # get image from Video
        img = cap.capture_array()
        y, x, _ = img.shape
        new_img = np.zeros(img.shape, np.uint8)
        
        # int32 tensor of shape: 256x256x3 / Channels order: RGB(0, 255)
        tf_img = cv2.resize(img, (256,256))
        tf_img = cv2.cvtColor(tf_img, cv2.COLOR_BGR2RGB)
        tf_img = np.asarray(tf_img)
        tf_img = np.expand_dims(tf_img,axis=0)
    
        # resize and pad image to fit tf model
        image = tf.cast(tf_img, dtype=tf.int32)
    
        # Run tfmodel
        outputs = movenet(image)
        # keypoints : [1, 1, 17, 3]
        keypoints = outputs['output_0']
    
        # Draw points on Keypoint
        threshold = 0.3
        keypoints_list = []
        color = (0, 255, 0)
        rad = 2
        thick = 2
        for k in keypoints[0,0,:,:]:
            # Converts to numpy array
            k = k.numpy()
    
            # Checks confidence for keypoint
            if k[2] > threshold:
                # The first two channels of the last dimension represents the yx coordinates (normalized to image frame, i.e. range in [0.0, 1.0]) of the 17 keypoints
                yc = int(k[0] * y)
                xc = int(k[1] * x)
    
                # Draws a circle on the image for each keypoint
                img = cv2.circle(img, (xc, yc), rad, color, thick)
                new_img = cv2.circle(new_img, (xc, yc), rad, color, thick)
                keypoints_list.append([xc, yc])
            else :
                keypoints_list.append(None)
        
        # Draw lines besides each keypoints
        color = (0, 255, 0)
        thick = 2
        body_conn = [[5,7], [7,9], [6,8], [8,10], [11,13], [13,15], [12,14], [14,16], [5, 6], [11, 12], [5, 11], [6, 12]]
        for conn in body_conn:
            p1, p2 = conn
            if keypoints_list[p1] is not None and keypoints_list[p2] is not None:
                img = cv2.line(img, keypoints_list[p1], keypoints_list[p2], color, thick)
                new_img = cv2.line(new_img, keypoints_list[p1], keypoints_list[p2], color, thick)
        
        # Framerate Checking
        '''
        # cv2.putText(img,'FPS: {0:.2f}'.format(frame_rate_calc),(30,50),cv2.FONT_HERSHEY_SIMPLEX,1,(255,255,0),2,cv2.LINE_AA)
        t2 = cv2.getTickCount()
        time1 = (t2-t1)/freq
        frame_rate_calc = 1/time1
        print(frame_rate_calc)
        '''
        
        # Shows image
        '''
        cv2.imshow('Cam', img)
        cv2.imshow('Skeleton', new_img)
        '''
        
        # jpeg encoding
        output_img = img if original else new_img
        ret, buffer = cv2.imencode('.jpg', output_img)
        frame = buffer.tobytes()
        yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')
        
        # Waits for the next frame, checks if q was pressed to quit
        '''
        if cv2.waitKey(1) == ord("q"):
            break
        '''
        # Reads next frame
        img = cap.capture_array()

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/video_feed')
def video_feed():
    return Response(calc_Ske(original = True), mimetype = 'multipart/x-mixed-replace; boundary=frame')

@app.route('/skeleton_feed')
def skeleton_feed():
    return Response(calc_Ske(original = False), mimetype = 'multipart/x-mixed-replace; boundary=frame')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
