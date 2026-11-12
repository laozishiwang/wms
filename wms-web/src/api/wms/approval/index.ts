import request from '@/config/axios'

export type ApprovalType = 'receipt' | 'shipment' | 'movement' | 'check'
export const ApprovalApi = {
  config: (): Promise<{ enabled: boolean }> => request.get({ url: '/wms/approval/config' }),
  submit: (type: ApprovalType, id: number) => request.post({ url: '/wms/approval/submit', data: { type, id } }),
  approve: (type: ApprovalType, id: number, comment?: string) => request.post({ url: '/wms/approval/approve', data: { type, id, comment } }),
  reject: (type: ApprovalType, id: number, comment?: string) => request.post({ url: '/wms/approval/reject', data: { type, id, comment } }),
  history: (type: ApprovalType, id: number) => request.get({ url: '/wms/approval/history', params: { type, id } })
}
