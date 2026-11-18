#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
视频 faststart 修复脚本：将 MinIO/S3 兼容存储中的 mp4 重封装（stream copy，
不转码、无画质损失），把 moov 元数据盒移到文件前部，浏览器无需下载大量数据即可起播。

用法：
  本机 MinIO:  venv-pxd/bin/python3 repair-faststart.py
  目标机 OSS:  venv-pxd/bin/python3 repair-faststart.py \
                --endpoint http://10.10.250.100:8333 \
                --ak <AK> --sk <SK> [--secure]
幂等：已 faststart 的 mp4 重写一次（内容基本不变），重复执行安全。
"""
import argparse, subprocess, tempfile, os, sys

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--endpoint', default='http://127.0.0.1:9000')
    ap.add_argument('--ak', default='minioadmin')
    ap.add_argument('--sk', default='minioadmin')
    ap.add_argument('--bucket', default='exam-files')
    ap.add_argument('--prefix', default='material/')
    ap.add_argument('--ffmpeg', default='/root/AIExamK3/middleware/ffmpeg')
    args = ap.parse_args()

    from minio import Minio
    client = Minio(args.endpoint.replace('http://', '').replace('https://', ''),
                   access_key=args.ak, secret_key=args.sk,
                   secure=args.endpoint.startswith('https'))
    ok, skip, fail = 0, 0, []
    for obj in client.list_objects(args.bucket, prefix=args.prefix, recursive=True):
        name = obj.object_name
        if not name.lower().endswith('.mp4'):
            continue
        try:
            with tempfile.NamedTemporaryFile(suffix='.mp4', delete=False) as t:
                tmp_in = t.name
            client.fget_object(args.bucket, name, tmp_in)
            with open(tmp_in, 'rb') as f:
                head = f.read(300000)
            if b'moov' in head:
                print(f'  跳过(已faststart): {name}')
                skip += 1
                os.unlink(tmp_in)
                continue
            tmp_out = tmp_in + '.fixed.mp4'
            p = subprocess.run([args.ffmpeg, '-y', '-i', tmp_in, '-c', 'copy',
                                '-movflags', 'faststart', tmp_out],
                               capture_output=True, text=True, timeout=600)
            if p.returncode != 0 or not os.path.exists(tmp_out):
                fail.append(name)
                print(f'  失败: {name}: {p.stderr[-200:]}')
                continue
            client.fput_object(args.bucket, name, tmp_out,
                               content_type='video/mp4')
            os.unlink(tmp_in); os.unlink(tmp_out)
            ok += 1
            print(f'  修复完成: {name}')
        except Exception as e:
            fail.append(name)
            print(f'  异常: {name}: {e}')
    print(f'\n完成：修复 {ok} 个，已ok跳过 {skip} 个，失败 {len(fail)} 个')
    for f in fail:
        print('  失败清单:', f)

if __name__ == '__main__':
    main()
