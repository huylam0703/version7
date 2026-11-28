import axiosClient from './axiosClient'

export interface Page<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export async function listAll<T>(
  url: string,
  params?: object
): Promise<T[]> {
  const { data } = await axiosClient.get<Page<T>>(url, {
    params: {
      ...params,
      page: 1,
      pageSize: 100,
    },
  })

  const items = [...data.items]

  const totalPages = Math.ceil(
    data.total / data.pageSize
  )

  for (let page = 2; page <= totalPages; page++) {
    const result = await axiosClient.get<Page<T>>(url, {
      params: {
        ...params,
        page,
        pageSize: 100,
      },
    })

    items.push(...result.data.items)
  }

  return items
}