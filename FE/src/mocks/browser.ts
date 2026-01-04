import { setupWorker } from 'msw/browser';
import { buildHandlers } from './handlers';

export const worker = setupWorker(...buildHandlers());
