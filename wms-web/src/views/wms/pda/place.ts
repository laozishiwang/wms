export type StoragePlace = { id: number; parentId?: number; type: string; code: string; name: string }

export const placePath = (places: StoragePlace[], id?: number) => {
  if (!id) return '未分配'
  const location = places.find((place) => place.id === id && place.type === 'LOCATION')
  if (!location) return `库位 ${id}`
  const area = places.find((place) => place.id === location.parentId && place.type === 'SHELF')
  return area ? `${area.name} / ${location.name}` : location.name
}
