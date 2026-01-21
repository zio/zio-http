$version: "2"
namespace example.orders

/// Order management service with resources
service OrderService {
    version: "2.0.0"
    resources: [Order]
    operations: [SearchOrders]
}

/// An order resource
resource Order {
    identifiers: { orderId: OrderId }
    properties: { status: OrderStatus }
    read: GetOrder
    create: CreateOrder
    update: UpdateOrder
    delete: CancelOrder
    list: ListOrders
    operations: [AddOrderItem, RemoveOrderItem]
}

/// Search orders across all users
@http(method: "POST", uri: "/orders/search")
@readonly
operation SearchOrders {
    input: SearchOrdersInput
    output: SearchOrdersOutput
}

/// Get order by ID
@http(method: "GET", uri: "/orders/{orderId}")
@readonly
operation GetOrder {
    input: GetOrderInput
    output: GetOrderOutput
    errors: [OrderNotFound]
}

/// Create a new order
@http(method: "POST", uri: "/orders")
operation CreateOrder {
    input: CreateOrderInput
    output: CreateOrderOutput
    errors: [ValidationError, InsufficientInventory]
}

/// Update an existing order
@http(method: "PUT", uri: "/orders/{orderId}")
@idempotent
operation UpdateOrder {
    input: UpdateOrderInput
    output: UpdateOrderOutput
    errors: [OrderNotFound, OrderAlreadyShipped, ValidationError]
}

/// Cancel an order
@http(method: "DELETE", uri: "/orders/{orderId}")
@idempotent
operation CancelOrder {
    input: CancelOrderInput
    output: CancelOrderOutput
    errors: [OrderNotFound, OrderAlreadyShipped]
}

/// List orders with filtering
@http(method: "GET", uri: "/orders")
@readonly
operation ListOrders {
    input: ListOrdersInput
    output: ListOrdersOutput
}

/// Add an item to an order
@http(method: "POST", uri: "/orders/{orderId}/items")
operation AddOrderItem {
    input: AddOrderItemInput
    output: AddOrderItemOutput
    errors: [OrderNotFound, OrderAlreadyShipped, InsufficientInventory]
}

/// Remove an item from an order
@http(method: "DELETE", uri: "/orders/{orderId}/items/{itemId}")
@idempotent
operation RemoveOrderItem {
    input: RemoveOrderItemInput
    output: RemoveOrderItemOutput
    errors: [OrderNotFound, ItemNotFound, OrderAlreadyShipped]
}

@length(min: 36, max: 36)
string OrderId

@length(min: 36, max: 36)
string ItemId

@length(min: 36, max: 36)
string ProductId

structure GetOrderInput {
    @required
    @httpLabel
    orderId: OrderId
}

structure GetOrderOutput {
    @required
    order: OrderData
}

structure CreateOrderInput {
    @required
    customerId: String

    @required
    items: OrderItemList

    shippingAddress: Address

    billingAddress: Address

    notes: String
}

structure CreateOrderOutput {
    @required
    order: OrderData
}

structure UpdateOrderInput {
    @required
    @httpLabel
    orderId: OrderId

    shippingAddress: Address

    billingAddress: Address

    notes: String
}

structure UpdateOrderOutput {
    @required
    order: OrderData
}

structure CancelOrderInput {
    @required
    @httpLabel
    orderId: OrderId

    @httpQuery("reason")
    reason: String
}

structure CancelOrderOutput {}

structure ListOrdersInput {
    @httpQuery("status")
    status: OrderStatus

    @httpQuery("customerId")
    customerId: String

    @httpQuery("fromDate")
    fromDate: Timestamp

    @httpQuery("toDate")
    toDate: Timestamp

    @httpQuery("limit")
    @range(min: 1, max: 100)
    limit: Integer

    @httpQuery("offset")
    @range(min: 0)
    offset: Integer
}

structure ListOrdersOutput {
    @required
    orders: OrderList

    @required
    total: Long
}

structure SearchOrdersInput {
    query: String

    filters: OrderFilters

    @range(min: 1, max: 100)
    limit: Integer

    offset: Integer
}

structure OrderFilters {
    statuses: OrderStatusList

    minTotal: Money

    maxTotal: Money

    dateRange: DateRange
}

structure DateRange {
    from: Timestamp
    to: Timestamp
}

structure SearchOrdersOutput {
    @required
    orders: OrderList

    @required
    total: Long
}

structure AddOrderItemInput {
    @required
    @httpLabel
    orderId: OrderId

    @required
    productId: ProductId

    @required
    @range(min: 1)
    quantity: Integer
}

structure AddOrderItemOutput {
    @required
    order: OrderData
}

structure RemoveOrderItemInput {
    @required
    @httpLabel
    orderId: OrderId

    @required
    @httpLabel
    itemId: ItemId
}

structure RemoveOrderItemOutput {
    @required
    order: OrderData
}

/// An order
structure OrderData {
    @required
    id: OrderId

    @required
    customerId: String

    @required
    items: OrderItemList

    @required
    status: OrderStatus

    @required
    subtotal: Money

    @required
    tax: Money

    @required
    total: Money

    shippingAddress: Address

    billingAddress: Address

    notes: String

    @required
    createdAt: Timestamp

    updatedAt: Timestamp

    shippedAt: Timestamp

    deliveredAt: Timestamp
}

/// An item in an order
structure OrderItem {
    @required
    id: ItemId

    @required
    productId: ProductId

    @required
    productName: String

    @required
    quantity: Integer

    @required
    unitPrice: Money

    @required
    totalPrice: Money
}

list OrderItemList {
    member: OrderItem
}

list OrderList {
    member: OrderData
}

list OrderStatusList {
    member: OrderStatus
}

enum OrderStatus {
    PENDING
    CONFIRMED
    PROCESSING
    SHIPPED
    DELIVERED
    CANCELLED
    REFUNDED
}

/// Monetary value in cents
structure Money {
    @required
    amount: Long

    @required
    currency: Currency
}

enum Currency {
    USD
    EUR
    GBP
    JPY
    CAD
    AUD
}

/// Shipping/billing address
structure Address {
    @required
    street1: String

    street2: String

    @required
    city: String

    @required
    state: String

    @required
    postalCode: String

    @required
    country: String
}

/// Order not found
@error("client")
@httpError(404)
structure OrderNotFound {
    @required
    message: String

    orderId: OrderId
}

/// Item not found in order
@error("client")
@httpError(404)
structure ItemNotFound {
    @required
    message: String

    itemId: ItemId
}

/// Order has already been shipped
@error("client")
@httpError(409)
structure OrderAlreadyShipped {
    @required
    message: String

    orderId: OrderId

    shippedAt: Timestamp
}

/// Insufficient inventory
@error("client")
@httpError(409)
structure InsufficientInventory {
    @required
    message: String

    productId: ProductId

    requested: Integer

    available: Integer
}

/// Validation error
@error("client")
@httpError(400)
structure ValidationError {
    @required
    message: String

    errors: ValidationErrorList
}

structure ValidationErrorDetail {
    @required
    field: String

    @required
    message: String
}

list ValidationErrorList {
    member: ValidationErrorDetail
}
