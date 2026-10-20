import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

export default mergeConfig(base, {
  server: {
    port: 4000,
    proxy: {
      '/api': 'http://localhost:9080',
    },
  },
});
