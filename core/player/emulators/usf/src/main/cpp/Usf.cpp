#include "Usf.h"

const char *last_error;

usf_loader_state * m_state;

void loadFile(const char *filename_c_str) {
    teardown();

    m_state = new usf_loader_state;
    m_state->emu_state = malloc( usf_get_state_size() );

    if (!m_state->emu_state) {
        last_error = "Bad allocation.";
        return;
    }

    usf_clear( m_state->emu_state );
    usf_set_hle_audio( m_state->emu_state, false);

    int ret = psf_load(
            filename_c_str,
            &psf_file_system,
            0x21,
            usf_loader,
            m_state,
            usf_info,
            m_state,
            0,
            0,
            0
    );

    if (ret < 0) {
        last_error = "Invalid USF";
        return;
    }

    usf_set_compare( m_state->emu_state, m_state->enable_compare );
    usf_set_fifo_full( m_state->emu_state, m_state->enable_fifo_full );
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_frames) {
    last_error = usf_render(m_state->emu_state, target_array, buffer_size_frames, nullptr);
    return buffer_size_frames;
}

void teardown() {
    if ( m_state )
    {
        usf_shutdown( m_state->emu_state );
        delete m_state;
        m_state = nullptr;
    }
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    int32_t sample_rate;
    usf_render(m_state->emu_state, nullptr, 0, &sample_rate);
    return sample_rate;
}

int usf_loader(void * context, const uint8_t * exe, size_t exe_size,
               const uint8_t * reserved, size_t reserved_size)
{
    auto * state = ( struct usf_loader_state * ) context;
    if ( exe_size > 0 ) return -1;

    return usf_upload_section( state->emu_state, reserved, reserved_size );
}

int usf_info(void * context, const char * name, const char * value)
{
    auto * state = ( struct usf_loader_state * ) context;

    if ( strcasecmp( name, "_enablecompare" ) == 0 && strlen( value ) )
        state->enable_compare = 1;
    else if ( strcasecmp( name, "_enablefifofull" ) == 0 && strlen( value ) )
        state->enable_fifo_full = 1;

    return 0;
}
