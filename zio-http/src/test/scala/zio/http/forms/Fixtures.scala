package zio.http.forms

import zio._

import java.nio.charset.StandardCharsets

object Fixtures {

  val CR = '\r'

  val base64Corgi =
    "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAACXBIWXMAAAsTAAALEwEAmpwYAAAFDUlEQVR4nO2YfUwbZRzHnzFRcWObQ1rAEDLRGXWZLjBwSwRfSabTUBzjD00kLJAYyXQ6ULJBkQFbZKCyuIUgvpBtKMStYerGENiAwpD2rjDoc9wdoxZaYOOttLAtCD/zFAqM8dIWijTpN/n+0T/ueT6fu6d39xxCjjjiiCPWRCaTObenBesGMl6421EQG4HsLbTkpwP/xLjDkFgIumPPjbQUHngc2VPkhbmleO+jRgHSru8jqpE9RV5dyVa95Ar6RIFRYDDVd0xzRvwYspdQmNNXBa2HG5EbJ69Cx49Rucge0tTU9CDN8FAf7gs1wesmr0Jv9su9yB5Csaw7EWiI2wUVAWtBHe02eRV0aU+O9Zx4vUt99sN4tFIjx9iLCFw/nQGqfVPwM9uZ954UrcRQSv4DIsCX5s4Jb6omPzoBraQUAqymGK6NCPSc3HUfcP9hIUT4bYIvXvMx/u7L2qFHKykKhoswLh/6Ghi+9LpPQB3vCUFP+04KkDbStQMU5qophs+plSk+y0xNjc37NuPZ/0WAYng5EVAVH11w+ZiKpReAHGO8czUqIWynP7zjvxWWHZ5muFATSHduuFnwhmQvaFTUTwqQ/nK2ACLffGN5Bei2tg0U5jtMEP1ZL84JrU/xJs8E6DjzETDVknvgTaUwd255BTCfPx2AAA5NAA+mbybvQqAqPgbK2ougwC2zQk90hMJ8Zo1a7bKc8IdngrSWnAJ92lOgkhwBhZKZDxgohh+kMfcHhfn9dU1tHlZBQAxyvn1IuGk40SN4KFEYqBcLt9yKc3Od7xh58w0fiuF+mA+OnrsaIq5g2W3k1msdNEKrhpM8wg1iocSQJLht7l3DVKayyBpwoDA3puB5gXXQYuSkESGRdo9Tqe7zjV2WQk9vT/aroFDOu55hrsqUKk+L4W+KkKdWhP7ShiHQxU295i6m6qIEqwQUytbdFsH3haP12lDUoBUh6IlyWRL48XpAW/FXlktgLtkiAW0oOk3gSXUHp7Z6SyWhOp9i2f+A4S6YDd/+LtqqCUWjJgFD0vgGY6nb/mucBRJcp9kCGhE6YoLX7lmFbQFvakfBJ+YKDJm/fERISuA1oWig833nLbYUIOVLTpqzhBizBagm5R1yUEN16VFy7zckCe7aUkCf5gsNjfQCf2L+Z7PgMcaupncMGcMYP2sYxELW9lchZ4HbKB9mlgDLsg/RDM9Nf7sbEgt+t62ABzBVv8139vmKiooHkLUZEguP2wq+PysQ+EvfzfsaQbe0voUWk75Mv/3mwHR/7A69B93NX/spPuQBtdAD7BBabLpzQkPmgtAlCEAV5Qb1IeuM33RI6X3PgyY9+M5gmu/oQhJkLzwbuByzcPmavA8tRTqKPt02c+K+eAE0ijbAlcBxaNLy7WvY8oC12WUBj4SIEXKCcLS668TuHV15Eem3Tr19tfeboPaB436DuvTNI4NpT4yS7eFsa7+sjobiK7VwvlyqsYmAIdkTaoJdx8EDXaE8YE3KVf+Hva0Zm2b49JkCknKpsTYT0KU/M3XWX/H+dzFj05iPXBaBgcztoM2PMW79GiovQUnsXvjz61QoPifpWszYFOZ22lxAPvHtcnJz0cwu2SR1SqWbzQVq1GoXGnPD0yepb2bhovTv8cnKqtslZdIga8c37nfv2X2xcLlWtiRjT03SwkXTmBuYPlHddTx1tsqk9daPzYtozN+c8yovYmxHHHEE2Vf+A9E0cuqjrJELAAAAAElFTkSuQmCC"

  val multipartFormBytes1 =
    Chunk.fromArray(s"""|--AaB03x${CR}
                        |Content-Disposition: form-data; name="submit-name"${CR}
                        |Content-Type: text/plain${CR}
                        |${CR}
                        |Larry${CR}
                        |--AaB03x${CR}
                        |Content-Disposition: form-data; name="files"; filename="file1.txt"${CR}
                        |Content-Type: image/png${CR}
                        |${CR}
                        |PNG${CR}
                        |--AaB03x${CR}
                        |Content-Disposition: form-data; name="corgi"; filename="corgi.png"${CR}
                        |Content-Type: image/png${CR}
                        |Content-Transfer-Encoding: base64${CR}
                        |${CR}
                        |${base64Corgi}${CR}
                        |--AaB03x--${CRLF}""".stripMargin.getBytes(StandardCharsets.UTF_8))

  val multipartFormBytes2 =
    Chunk.fromArray(
      s"""|--(((AaB03x)))${CR}
          |Content-Disposition: form-data; name="csv-data"${CR}
          |Content-Type: text/csv${CR}
          |${CR}
          |foo,bar,baz${CR}
          |--(((AaB03x)))${CR}
          |Content-Disposition: form-data; name="file"${CR}
          |Content-Type: image/png${CR}
          |${CR}
          |PNG${CR}
          |--(((AaB03x)))${CR}
          |Content-Disposition: form-data; name="corgi"; filename="corgi.png"${CR}
          |Content-Type: image/png${CR}
          |Content-Transfer-Encoding: base64${CR}
          |${CR}
          |iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAACXBIWXMAAAsTAAALEwEAmpwYAAAFDUlEQVR4nO2YfUwbZRzHnzFRcWObQ1rAEDLRGXWZLjBwSwRfSabTUBzjD00kLJAYyXQ6ULJBkQFbZKCyuIUgvpBtKMStYerGENiAwpD2rjDoc9wdoxZaYOOttLAtCD/zFAqM8dIWijTpN/n+0T/ueT6fu6d39xxCjjjiiCPWRCaTObenBesGMl6421EQG4HsLbTkpwP/xLjDkFgIumPPjbQUHngc2VPkhbmleO+jRgHSru8jqpE9RV5dyVa95Ar6RIFRYDDVd0xzRvwYspdQmNNXBa2HG5EbJ69Cx49Rucge0tTU9CDN8FAf7gs1wesmr0Jv9su9yB5Csaw7EWiI2wUVAWtBHe02eRV0aU+O9Zx4vUt99sN4tFIjx9iLCFw/nQGqfVPwM9uZ954UrcRQSv4DIsCX5s4Jb6omPzoBraQUAqymGK6NCPSc3HUfcP9hIUT4bYIvXvMx/u7L2qFHKykKhoswLh/6Ghi+9LpPQB3vCUFP+04KkDbStQMU5qophs+plSk+y0xNjc37NuPZ/0WAYng5EVAVH11w+ZiKpReAHGO8czUqIWynP7zjvxWWHZ5muFATSHduuFnwhmQvaFTUTwqQ/nK2ACLffGN5Bei2tg0U5jtMEP1ZL84JrU/xJs8E6DjzETDVknvgTaUwd255BTCfPx2AAA5NAA+mbybvQqAqPgbK2ougwC2zQk90hMJ8Zo1a7bKc8IdngrSWnAJ92lOgkhwBhZKZDxgohh+kMfcHhfn9dU1tHlZBQAxyvn1IuGk40SN4KFEYqBcLt9yKc3Od7xh58w0fiuF+mA+OnrsaIq5g2W3k1msdNEKrhpM8wg1iocSQJLht7l3DVKayyBpwoDA3puB5gXXQYuSkESGRdo9Tqe7zjV2WQk9vT/aroFDOu55hrsqUKk+L4W+KkKdWhP7ShiHQxU295i6m6qIEqwQUytbdFsH3haP12lDUoBUh6IlyWRL48XpAW/FXlktgLtkiAW0oOk3gSXUHp7Z6SyWhOp9i2f+A4S6YDd/+LtqqCUWjJgFD0vgGY6nb/mucBRJcp9kCGhE6YoLX7lmFbQFvakfBJ+YKDJm/fERISuA1oWig833nLbYUIOVLTpqzhBizBagm5R1yUEN16VFy7zckCe7aUkCf5gsNjfQCf2L+Z7PgMcaupncMGcMYP2sYxELW9lchZ4HbKB9mlgDLsg/RDM9Nf7sbEgt+t62ABzBVv8139vmKiooHkLUZEguP2wq+PysQ+EvfzfsaQbe0voUWk75Mv/3mwHR/7A69B93NX/spPuQBtdAD7BBabLpzQkPmgtAlCEAV5Qb1IeuM33RI6X3PgyY9+M5gmu/oQhJkLzwbuByzcPmavA8tRTqKPt02c+K+eAE0ijbAlcBxaNLy7WvY8oC12WUBj4SIEXKCcLS668TuHV15Eem3Tr19tfeboPaB436DuvTNI4NpT4yS7eFsa7+sjobiK7VwvlyqsYmAIdkTaoJdx8EDXaE8YE3KVf+Hva0Zm2b49JkCknKpsTYT0KU/M3XWX/H+dzFj05iPXBaBgcztoM2PMW79GiovQUnsXvjz61QoPifpWszYFOZ22lxAPvHtcnJz0cwu2SR1SqWbzQVq1GoXGnPD0yepb2bhovTv8cnKqtslZdIga8c37nfv2X2xcLlWtiRjT03SwkXTmBuYPlHddTx1tsqk9daPzYtozN+c8yovYmxHHHEE2Vf+A9E0cuqjrJELAAAAAElFTkSuQmCC${CR}
          |--(((AaB03x)))--${CRLF}""".stripMargin.getBytes(),
    )
}
