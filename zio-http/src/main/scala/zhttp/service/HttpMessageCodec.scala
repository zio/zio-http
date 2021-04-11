package zhttp.service

trait HttpMessageCodec extends DecodeJRequest with DecodeJResponse with EncodeRequest with EncodeResponse {}
