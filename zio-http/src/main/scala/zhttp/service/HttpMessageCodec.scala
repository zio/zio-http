package zhttp.service

trait HttpMessageCodec extends DecodeJResponse with DecodeJRequest with EncodeClientParams {}
