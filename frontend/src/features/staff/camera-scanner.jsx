import { useEffect, useRef, useState } from 'react';

function ticketToken(rawValue) {
  try {
    const payload = JSON.parse(rawValue);
    return typeof payload.r === 'string' && payload.r ? payload.r : rawValue;
  } catch {
    return rawValue;
  }
}

export function CameraScanner({ onToken, onClose }) {
  const videoRef = useRef(null);
  const [message, setMessage] = useState('Requesting camera access…');

  useEffect(() => {
    let stream;
    let timer;
    let closed = false;
    const start = async () => {
      if (!window.BarcodeDetector) {
        setMessage('Camera scanning is unavailable in this browser. Enter the ticket token manually.');
        return;
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
        if (closed) return;
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        setMessage('Point the camera at the ticket QR code.');
        const scan = async () => {
          if (closed) return;
          const [code] = await detector.detect(videoRef.current);
          if (code?.rawValue) { onToken(ticketToken(code.rawValue)); onClose(); return; }
          timer = window.setTimeout(scan, 250);
        };
        scan();
      } catch {
        setMessage('Camera access was not granted. Enter the ticket token manually.');
      }
    };
    start();
    return () => { closed = true; window.clearTimeout(timer); stream?.getTracks().forEach((track) => track.stop()); };
  }, [onClose, onToken]);

  return <section className="camera-scanner" aria-labelledby="camera-scanner-heading">
    <h4 id="camera-scanner-heading">Scan ticket QR</h4>
    <video ref={videoRef} muted playsInline aria-label="Camera preview" />
    <p role="status">{message}</p>
    <button type="button" onClick={onClose}>Use manual entry</button>
  </section>;
}
