/* eslint-disable @typescript-eslint/no-unused-vars */
import 'axios';

declare module 'axios' {
  // 둘 다 보강해두면 편합니다.
  interface AxiosRequestConfig<D = any> {
    _retry?: boolean;
  }
  interface InternalAxiosRequestConfig<D = any> {
    _retry?: boolean;
  }
}
