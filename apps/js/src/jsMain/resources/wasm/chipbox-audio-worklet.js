// Chipbox audio worklet — pulls PCM frames from a FIFO queue filled by the main thread, resamples
// from each buffer's source rate to the AudioContext's native rate via linear interpolation, and
// writes into the output AudioBuffer.
//
// Each pushed message carries an interleaved L/R Float32Array plus the `srcRate` it was rendered
// at (different chiptune emulators output different rates — libgme: 44100 for NSF/GBS/etc., 32000
// for SPC; future emulators add more). The AudioContext rate (`sampleRate` global, hardware-
// dependent, usually 44100 or 48000) is what we must produce. The mismatch is handled here so
// the producer can stay rate-agnostic.
//
// Linear interpolation is plenty for chiptune — the source content is already band-limited by
// the emulator's own DAC model, and the rate ratios in play (~32k→48k, ~44.1k→48k) don't
// introduce audible aliasing at this fidelity.
//
// This file ships in the bundle at /wasm/chipbox-audio-worklet.js (see WebAudioSpeaker.kt's
// `audioWorklet.addModule(...)` call). Plain JS — the Kotlin side talks to it via MessagePort.

class ChipboxAudioProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        // FIFO of { samples: Float32Array (interleaved L/R), srcRate: number (Hz) }.
        this.queue = [];
        // Read cursor inside the head buffer, in *source* frames (one frame = two samples L+R)
        // plus a fractional offset in [0, 1) that drives the linear interp.
        this.readFrame = 0;
        this.readFraction = 0;
        // Cumulative count of fully-consumed source frames since the last flush. Posted back to
        // the Kotlin side after each head-buffer shift so `WebAudioSpeaker.awaitSinkCapacity`
        // can throttle the consume loop to actual playback rate.
        this.consumedSinceFlush = 0;

        this.port.onmessage = (e) => {
            const msg = e.data;
            switch (msg && msg.type) {
                case 'buffer':
                    this.queue.push({ samples: msg.samples, srcRate: msg.srcRate });
                    break;
                case 'flush':
                    this.queue = [];
                    this.readFrame = 0;
                    this.readFraction = 0;
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
        const dstRate = sampleRate;  // AudioWorkletGlobalScope global = AudioContext.sampleRate.

        for (let i = 0; i < frames; i++) {
            if (this.queue.length === 0) {
                left[i] = 0;
                right[i] = 0;
                continue;
            }

            // Step = source frames per output frame. <1 when upsampling (e.g. 32k→48k = 0.667),
            // >1 when downsampling (e.g. 48k→44.1k = 1.088, hypothetical).
            const head = this.queue[0];
            const headSamples = head.samples;
            const headFrames = headSamples.length >>> 1;
            const step = head.srcRate / dstRate;

            // Pair of source frames straddling our fractional cursor; lerp by readFraction.
            const a = this.readFrame;
            const t = this.readFraction;
            const aIdx = a << 1;
            const aL = headSamples[aIdx];
            const aR = headSamples[aIdx + 1];

            let bL, bR;
            if (a + 1 < headFrames) {
                const bIdx = (a + 1) << 1;
                bL = headSamples[bIdx];
                bR = headSamples[bIdx + 1];
            } else if (this.queue.length > 1) {
                // Frame N+1 is the first frame of the next queued buffer. Peek without consuming
                // — the buffer-advance below will shift when the cursor actually crosses.
                const next = this.queue[1].samples;
                bL = next[0];
                bR = next[1];
            } else {
                // Last source frame in the entire queue and nothing else coming. Repeat the
                // current frame so the linear interp degenerates to a hold rather than fading
                // toward an arbitrary zero / undefined value.
                bL = aL;
                bR = aR;
            }

            left[i] = aL + (bL - aL) * t;
            right[i] = aR + (bR - aR) * t;

            // Advance the fractional cursor.
            this.readFraction += step;
            while (this.readFraction >= 1.0) {
                this.readFraction -= 1.0;
                this.readFrame += 1;
            }
            // Drain consumed source buffers. `while` (not `if`) because at high downsampling
            // ratios a single output frame could in principle stride past more than one buffer.
            // Each shift reports cumulative consumption back to the main thread so the
            // WebAudioSpeaker side can throttle its consume loop.
            while (
                this.queue.length > 0 &&
                this.readFrame >= (this.queue[0].samples.length >>> 1)
            ) {
                const headFramesConsumed = this.queue[0].samples.length >>> 1;
                this.readFrame -= headFramesConsumed;
                this.queue.shift();
                this.consumedSinceFlush += headFramesConsumed;
                this.port.postMessage({ type: 'consumed', frames: this.consumedSinceFlush });
            }
        }
        return true;
    }
}

registerProcessor('chipbox-audio', ChipboxAudioProcessor);
