#define XSF_FALSE (0)
#define XSF_TRUE (!XSF_FALSE)

#ifdef __cplusplus
extern "C" {
#endif

//int xsf_start(void *pfile, unsigned bytes);
int xsf_start(char *filename);
int xsf_gen(void *pbuffer, unsigned samples);
void xsf_term(void);

#ifdef __cplusplus
}
#endif
