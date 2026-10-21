import * as TabsPrimitive from '@radix-ui/react-tabs'
import type { ComponentProps } from 'react'

// shadcn/ui-style Radix composition, styled by the application's shared tokens.
export function Tabs(props: ComponentProps<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root data-slot="tabs" {...props} />
}
export function TabsList({ className = '', ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  return <TabsPrimitive.List data-slot="tabs-list" className={`ui-tabs-list ${className}`} {...props} />
}
export function TabsTrigger({ className = '', ...props }: ComponentProps<typeof TabsPrimitive.Trigger>) {
  return <TabsPrimitive.Trigger data-slot="tabs-trigger" className={`ui-tabs-trigger ${className}`} {...props} />
}
export function TabsContent({ className = '', ...props }: ComponentProps<typeof TabsPrimitive.Content>) {
  return <TabsPrimitive.Content data-slot="tabs-content" className={`ui-tabs-content ${className}`} {...props} />
}
