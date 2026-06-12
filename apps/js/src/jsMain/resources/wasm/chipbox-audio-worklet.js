// Chipbox audio worklet — a FIFO that plays interleaved L/R Float32 frames already at the
// AudioContext's rate. The Kotlin side (WebAudioSpeaker) resamples each emulator buffer to the
// context rate via the shared multiplatform Resampler before posting, so there's no rate
// conversion here — this just drains the queue one frame per output frame and reports progress.
//
// Each pushed message carries an interleaved L/R Float32Array at the context rate. The worklet
// posts a `{type: "consumed", frames}` message back after each head-buffer shift so
// WebAudioSpeaker.awaitSinkCapacity can throttle the consume loop to actual playback rate.
//
// This file ships in the bundle at /wasm/chipbox-audio-worklet.js (see WebAudioSpeaker.kt's
// `audioWorklet.addModule(...)` call). Plain JS — the Kotlin side talks to it via MessagePort.

class ChipboxAudioProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        // FIFO of Float32Array (interleaved L/R, at the AudioContext rate).
        this.queue = [];
        // Read cursor inside the head buffer, in frames (one frame = two samples L+R).
        this.readFrame = 0;
        // Cumulative count of fully-consumed frames since the last flush, posted back so
        // WebAudioSpeaker can throttle its consume loop to actual playback rate.
        this.consumedSinceFlush = 0;

        this.port.onmessage = (e) => {
            const msg = e.data;
            switch (msg && msg.type) {
                case 'buffer':
                    this.queue.push(msg.samples);
                    break;
                case 'flush':
                    this.queue = [];
                    this.readFrame = 0;
                    this.consumedSinceFlush = 0;
                    this.port.postMessage({ type: 'consumed', frames: 0 });
                    break;
            }
        };
    }

    process(inputs, outputs) {
        const channels = outputs[0];
        const left = channels[0];
        const right = channels.length > 1 ? channels[1] : channels[0];
        const frames = left.length;

        for (let i = 0; i < frames; i++) {
            if (this.queue.length === 0) {
                left[i] = 0;
                right[i] = 0;
                continue;
            }

            const head = this.queue[0];
            const idx = this.readFrame << 1;
            left[i] = head[idx];
            right[i] = head[idx + 1];
            this.readFrame += 1;

            // Drain consumed buffers. `while` (not `if`) so an empty buffer can't wedge the cursor.
            // Each shift reports cumulative consumption back to the main thread.
            while (
                this.queue.length > 0 &&
                this.readFrame >= (this.queue[0].length >>> 1)
            ) {
                const headFrames = this.queue[0].length >>> 1;
                this.readFrame -= headFrames;
                this.queue.shift();
                this.consumedSinceFlush += headFrames;
                this.port.postMessage({ type: 'consumed', frames: this.consumedSinceFlush });
            }
        }
        return true;
    }
}

registerProcessor('chipbox-audio', ChipboxAudioProcessor);
