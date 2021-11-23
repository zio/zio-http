package zhttp.service

trait HttpMessageCodec extends DecodeJRequest with DecodeJResponse with EncodeClientParams with EncodeResponse {}
