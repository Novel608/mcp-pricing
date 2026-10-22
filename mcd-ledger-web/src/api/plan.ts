import http, { type HttpConfig } from './http'
import type {
  AddPlanParticipantPayload,
  CreatePlanPayload,
  MealDetail,
  MenuResponse,
  PlanDetail,
  SubmitSelectionsPayload,
} from '@/types/plan'

/**
 * 选餐域 REST 客户端（增量设计 §3.4，本次只用端点 1–6 与 13）。
 *
 * 注意：http 拦截器已统一解包 ApiResponse，这里的返回值就是 `data`。
 */
export const planApi = {
  /** 1. 创建方案 */
  createPlan(payload: CreatePlanPayload): Promise<PlanDetail> {
    return http.post('/plans', payload) as unknown as Promise<PlanDetail>
  },

  /** 2. 方案全量状态（成员 / 选餐 / 聚合） */
  getPlan(id: number): Promise<PlanDetail> {
    return http.get(`/plans/${id}`) as unknown as Promise<PlanDetail>
  },

  /** 3. 添加成员（含项目绑定） */
  addParticipant(id: number, payload: AddPlanParticipantPayload): Promise<PlanDetail> {
    return http.post(`/plans/${id}/participants`, payload) as unknown as Promise<PlanDetail>
  },

  /** 4. 代理 query-meals（后端缓存 10min）；后端应已将元字符串转成分 */
  listMeals(storeId: string): Promise<MenuResponse> {
    return http.get('/mcp/meals', { params: { storeId }, silent: true } as HttpConfig) as unknown as Promise<MenuResponse>
  },

  /** 5. 代理 query-meal-detail（后端缓存 30min） */
  getMeal(itemId: string): Promise<MealDetail> {
    return http.get(`/mcp/meals/${encodeURIComponent(itemId)}`, {
      silent: true,
    } as HttpConfig) as unknown as Promise<MealDetail>
  },

  /**
   * 6. 提交当前成员选餐（按成员幂等整份覆盖）。
   * participantId 同时放 query param 与 body，兼容后端两种取法。
   */
  submitSelections(id: number, payload: SubmitSelectionsPayload): Promise<PlanDetail> {
    return http.put(`/plans/${id}/selections`, payload, {
      params: { participantId: payload.participantId },
    } as HttpConfig) as unknown as Promise<PlanDetail>
  },

  /** 13. 沙盒选餐方案（无需 Token） */
  getDemoPlan(): Promise<PlanDetail> {
    return http.get('/demo/plan') as unknown as Promise<PlanDetail>
  },
}
