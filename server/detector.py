from ctypes import *
import random
import socket
from timeit import default_timer as timer
import struct
import numpy as np
import cv2
import demjson
import os
import multiprocessing as mp
import time
from datetime import datetime
import re
import copy
from multiprocessing.reduction import recv_handle, send_handle

REPO_FOLD = os.path.abspath(os.path.join(os.getcwd(), '../'))
DARKNET_LIB = './libdarknet.so'
YOLO608_WEIGHTS = bytes('./cfg/yolov3_608.cfg', encoding='utf8')
YOLO576_WEIGHTS = bytes('./cfg/yolov3_576.cfg', encoding='utf8')
YOLO544_WEIGHTS = bytes('./cfg/yolov3_544.cfg', encoding='utf8')
YOLO512_WEIGHTS = bytes('./cfg/yolov3_512.cfg', encoding='utf8')
YOLO480_WEIGHTS = bytes('./cfg/yolov3_480.cfg', encoding='utf8')
YOLO448_WEIGHTS = bytes('./cfg/yolov3_448.cfg', encoding='utf8')
YOLO416_WEIGHTS = bytes('./cfg/yolov3_416.cfg', encoding='utf8')
YOLO384_WEIGHTS = bytes('./cfg/yolov3_384.cfg', encoding='utf8')
YOLO352_WEIGHTS = bytes('./cfg/yolov3_352.cfg', encoding='utf8')
YOLO320_WEIGHTS = bytes('./cfg/yolov3_320.cfg', encoding='utf8')
YOLO288_WEIGHTS = bytes('./cfg/yolov3_288.cfg', encoding='utf8')
YOLOV3_WEIGHTS = bytes('./cfg/yolov3.weights', encoding='utf8')
COCO_DATA = bytes('./cfg/coco.data', encoding='utf8')
GPU_TRACE = REPO_FOLD + '/GPU_trace/'
BIND_IP = '0.0.0.0'   # IP of the detector
# BIND_IP = '124.71.205.183'   # IP of the detector

MAX_GPU_MEMORY = 12 * 1024
YOLO_MEMORY = [2531, 2365, 2289, 1945, 1885, 1761, 1589, 1501, 1383, 1281, 1207]
RESOLUTION_MIN = 288
RESOLUTION_MAX = 608
RESOLUTION_STEP = 32
MODEL_RESOLUTION = 11        # the number of knob of resolution


def sample(probs):
    s = sum(probs)
    probs = [a / s for a in probs]
    r = random.uniform(0, 1)
    for i in range(len(probs)):
        r = r - probs[i]
        if r <= 0:
            return i
    return len(probs) - 1


def c_array(ctype, values):
    arr = (ctype * len(values))()
    arr[:] = values
    return arr


class BOX(Structure):
    _fields_ = [("x", c_float),
                ("y", c_float),
                ("w", c_float),
                ("h", c_float)]


class DETECTION(Structure):
    _fields_ = [("bbox", BOX),
                ("classes", c_int),
                ("prob", POINTER(c_float)),
                ("mask", POINTER(c_float)),
                ("objectness", c_float),
                ("sort_class", c_int)]


class IMAGE(Structure):
    _fields_ = [("w", c_int),
                ("h", c_int),
                ("c", c_int),
                ("data", POINTER(c_float))]


class METADATA(Structure):
    _fields_ = [("classes", c_int),
                ("names", POINTER(c_char_p))]


lib = CDLL(DARKNET_LIB, RTLD_GLOBAL)
lib.network_width.argtypes = [c_void_p]
lib.network_width.restype = c_int
lib.network_height.argtypes = [c_void_p]
lib.network_height.restype = c_int

predict = lib.network_predict
predict.argtypes = [c_void_p, POINTER(c_float)]
predict.restype = POINTER(c_float)

set_gpu = lib.cuda_set_device
set_gpu.argtypes = [c_int]

make_image = lib.make_image
make_image.argtypes = [c_int, c_int, c_int]
make_image.restype = IMAGE

get_network_boxes = lib.get_network_boxes
get_network_boxes.argtypes = [c_void_p, c_int, c_int, c_float, c_float, POINTER(c_int), c_int, POINTER(c_int)]
get_network_boxes.restype = POINTER(DETECTION)

make_network_boxes = lib.make_network_boxes
make_network_boxes.argtypes = [c_void_p]
make_network_boxes.restype = POINTER(DETECTION)

free_detections = lib.free_detections
free_detections.argtypes = [POINTER(DETECTION), c_int]

free_ptrs = lib.free_ptrs
free_ptrs.argtypes = [POINTER(c_void_p), c_int]

network_predict = lib.network_predict
network_predict.argtypes = [c_void_p, POINTER(c_float)]

reset_rnn = lib.reset_rnn
reset_rnn.argtypes = [c_void_p]

load_net = lib.load_network
load_net.argtypes = [c_char_p, c_char_p, c_int]
load_net.restype = c_void_p

do_nms_obj = lib.do_nms_obj
do_nms_obj.argtypes = [POINTER(DETECTION), c_int, c_int, c_float]

do_nms_sort = lib.do_nms_sort
do_nms_sort.argtypes = [POINTER(DETECTION), c_int, c_int, c_float]

free_image = lib.free_image
free_image.argtypes = [IMAGE]

letterbox_image = lib.letterbox_image
letterbox_image.argtypes = [IMAGE, c_int, c_int]
letterbox_image.restype = IMAGE

load_meta = lib.get_metadata
lib.get_metadata.argtypes = [c_char_p]
lib.get_metadata.restype = METADATA

load_image = lib.load_image_color
load_image.argtypes = [c_char_p, c_int, c_int]
load_image.restype = IMAGE

ndarray_image = lib.ndarray_to_image
ndarray_image.argtypes = [POINTER(c_ubyte), POINTER(c_long), POINTER(c_long)]
ndarray_image.restype = IMAGE

rgbgr_image = lib.rgbgr_image
rgbgr_image.argtypes = [IMAGE]

predict_image = lib.network_predict_image
predict_image.argtypes = [c_void_p, IMAGE]
predict_image.restype = POINTER(c_float)


def classify(net, meta, im):
    out = predict_image(net, im)
    res = []
    for i in range(meta.classes):
        res.append((meta.names[i], out[i]))
    res = sorted(res, key=lambda x: -x[1])
    return res


def nparray_to_image(img):
    # print('img: ', img)
    data = img.ctypes.data_as(POINTER(c_ubyte))
    image = ndarray_image(data, img.ctypes.shape, img.ctypes.strides)
    return image


def detect(net, meta, im, thresh=.5, hier_thresh=.5, nms=.45):
    num = c_int(0)
    pnum = pointer(num)
    predict_image(net, im)
    dets = get_network_boxes(net, im.w, im.h, thresh, hier_thresh, None, 0, pnum)
    num = pnum[0]
    if (nms): do_nms_obj(dets, num, meta.classes, nms);

    res = []
    for j in range(num):
        for i in range(meta.classes):
            if dets[j].prob[i] > 0:
                b = dets[j].bbox
                res.append((i + 1, dets[j].prob[i], (b.x, b.y, b.w, b.h)))    # i+1目的是与coco标签对应，因为标签是从1开始的
    res = sorted(res, key=lambda x: -x[1])
    free_image(im)
    free_detections(dets, num)
    return res  # [(物体id,置信度,(坐标)),(物体id,置信度,(坐标)),(物体id,置信度,(坐标))]


def initialize_socket():
    s_client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)  # 初始化socket,这个用来接收client的图片
    s_client.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # 端口可复用
    s_client.bind((BIND_IP, 10003))
    s_client.listen(5)  # 最大连接数为5

    print('connect ok')
    return s_client


def connect_client(socketobj, str):
    socketobj.send(str)


def connect_controller(socketobj, str):
    socketobj.send(str)


def append_image_info(im, frame_count, c):
    image_info = []
    image_info.append(im)
    image_info.append(frame_count)

    image_info.append(c)
    return image_info


def write_result(q_result):
    # status = 0: frame receiving start
    # status = 1: frame receiving finish
    # status = 2: return the result

    starttime = datetime.now().strftime('%m%d%H%M')
    f_result = open('./result/results_' + starttime + '.txt', 'w')
    while True:
        [index, status, time] = q_result.get(True)
        f_result.write(str(index) + '\t' + str(status) + '\t' + time + '\n')
        f_result.flush()


def detect_frame_gpu0(q_frame_gpu0, c1, c2, q_result):
    os.environ['CUDA_VISIBLE_DEVICES'] = '0'

    net1 = load_net(YOLO288_WEIGHTS, YOLOV3_WEIGHTS, 0)

    meta = load_meta(COCO_DATA)

    print('finish loading network')

    print("receiving the fd...")
    fd = recv_handle(c1)
    print(f"get the fd {fd}")
    c = socket.socket(socket.AF_INET, socket.SOCK_STREAM, fileno=fd)

    while True:
        print("getting frame from queue...")
        [img_str, frame_count] = q_frame_gpu0.get(True)
        print("finish getting frame")
        # print(datetime.now().strftime('%H:%M:%S.%f')[:-3], 'the queue receive frame', frame_count, '\n')
        img_array = np.fromstring(img_str, dtype='uint8')  # 转换成array
        img_decoded = cv2.imdecode(img_array, 1)  # 解压图片,img为array格式

        im = nparray_to_image(img_decoded)
        r = detect(net1, meta, im)
        r_json = demjson.encode(r)  # r_json类型为str
        r_json += '**\n'
        # print(r_json)
        # str_rJsonLen_resolutionNum_imgID_rJson = \
        #     struct.pack('h', len(r_json)) + bytes(r_json, encoding="utf8")
        str_rJsonLen_resolutionNum_imgID_rJson = bytes(r_json, encoding="utf8")

        c.send(str_rJsonLen_resolutionNum_imgID_rJson)  # 把识别结果返回给client
        q_result.put([frame_count, 2, datetime.now().strftime('%H:%M:%S.%f')[:-3]])
        print(datetime.now().strftime('%H:%M:%S.%f')[:-3], 'return result of ', frame_count, '\n')


def handle(clientsocket, q_frame_gpu0, index, c1, c2, worker_pid, q_result):
    print("receiving image length...")
    len_bytes = clientsocket.recv(4)
    if len(len_bytes) == 0: # socket connection is closed
        return -1

    img_strlen = struct.unpack('>I', len_bytes)[0]
    q_result.put([index, 0, datetime.now().strftime('%H:%M:%S.%f')[:-3]])
    # print(datetime.now().strftime('%H:%M:%S.%f')[:-3], index, "the img's length: ", img_strlen)
    cur_size = 0
    img_str = bytes()
    while cur_size < img_strlen:
        tmp_str = clientsocket.recv(img_strlen - cur_size)
        img_str += tmp_str
        cur_size += len(tmp_str)
        # print(datetime.now().strftime('%H:%M:%S.%f')[:-3], index, "current size is: ", cur_size)
    # print(f"cur_size {cur_size}")

    q_result.put([index, 1, datetime.now().strftime('%H:%M:%S.%f')[:-3]])

    print(datetime.now().strftime('%H:%M:%S.%f')[:-3], 'put frame', index, 'to queue \n')
    q_frame_gpu0.put([img_str, index])
    return 0

def handler(clientsocket, q_frame_gpu0, index, c1, c2, worker_pid, q_result):
    send_handle(c2, clientsocket.fileno(), worker_pid)
    index = 0
    while handle(clientsocket, q_frame_gpu0, index, c1, c2, worker_pid, q_result) != -1:
        index += 1
    print("handler finished")

def main():
    if not os.path.exists('./result/'):
        os.mkdir('./result/')

    c1, c2 = mp.Pipe()
    q_frame_gpu0 = mp.Queue()
    q_result = mp.Queue()
    p1 = mp.Process(target=write_result, args=(q_result, ))
    p2 = mp.Process(target=detect_frame_gpu0, args=(q_frame_gpu0, c1, c2, q_result))

    p1.start()
    p2.start()
    # p1.join()
    # p2.join()

    serversocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    host = "0.0.0.0"
    port = 10003
    serversocket.bind((host, port))

    serversocket.listen(5)
    print('listening on port 10003...')

    index = 0
    while True:
        print("receiving the client...")
        clientsocket, addr = serversocket.accept()
        # print(datetime.now().strftime('%H:%M:%S.%f')[:-3], index, "socket accept: ", addr)

        p = mp.Process(target=handler, args=(clientsocket, q_frame_gpu0, index, c1, c2, p2.pid, q_result))
        index += 1
        p.start()


if __name__ == "__main__":
    main()
