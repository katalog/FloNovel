-- Validate the certificate chain and hostname before writing HTTP credentials.
local socket,ssl,ffi=require("socket"),require("ssl"),require("ffi")
require("ffi/posix_h")
ffi.cdef[[
typedef struct bio_st BIO;
typedef struct x509_st X509;
BIO *BIO_new_mem_buf(const void *buf, int len);
int BIO_free(BIO *a);
X509 *PEM_read_bio_X509(BIO *bp, X509 **x, void *cb, void *u);
void X509_free(X509 *a);
int X509_check_host(X509 *x, const char *name, size_t namelen, unsigned int flags, char **peername);
int X509_check_ip_asc(X509 *x, const char *ipasc, unsigned int flags);
]]
local crypto=ffi.loadlib("crypto","57")
local M={}
function M.check_name(pem,host)
    local bio=crypto.BIO_new_mem_buf(pem,#pem)
    if bio==nil then return false end
    local cert=crypto.PEM_read_bio_X509(bio,nil,nil,nil)
    crypto.BIO_free(bio)
    if cert==nil then return false end
    local result
    if host:match("^[0-9.]+$") or host:find(":",1,true) then
        result=crypto.X509_check_ip_asc(cert,host,0)
    else result=crypto.X509_check_host(cert,host,#host,0,nil) end
    crypto.X509_free(cert)
    return result==1
end
function M.create(cafile)
    return function()
        local conn={sock=assert(socket.tcp())}
        function conn:settimeout(seconds) return self.sock:settimeout(math.min(seconds or 5, 5)) end
        function conn:close() return self.sock:close() end
        function conn:connect(host,port)
            local ok,err=self.sock:connect(host,port)
            if not ok then return nil,err end
            local wrapped
            wrapped,err=ssl.wrap(self.sock,{mode="client",protocol="any",cafile=cafile,
                verify={"peer","fail_if_no_peer_cert"},options={"all","no_sslv2","no_sslv3","no_tlsv1","no_tlsv1_1"}})
            if not wrapped then return nil,err end
            self.sock=wrapped
            self.sock:sni(host)
            self.sock:settimeout(5)
            ok,err=self.sock:dohandshake()
            if not ok then return nil,err end
            local certificate=self.sock:getpeercertificate()
            if not certificate or not M.check_name(certificate:pem(),host) then
                self.sock:close();return nil,"certificate_hostname_mismatch"
            end
            return 1
        end
        return setmetatable(conn,{__index=function(self,key)
            return function(_,...) return self.sock[key](self.sock,...) end
        end})
    end
end
return M
