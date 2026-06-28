export function closePrintWindow(printWindow) {
  if (printWindow && !printWindow.closed) {
    printWindow.close()
  }
}

export function writePrintDocument(printWindow, html) {
  printWindow.document.write(html)
  printWindow.document.close()
}

export function printWhenLoaded(printWindow, {
  delay = 300,
  onReady,
  onAfterDialogOpen,
  onError
} = {}) {
  printWindow.onload = () => {
    onReady?.()

    setTimeout(() => {
      try {
        printWindow.focus()
        printWindow.print()
        onAfterDialogOpen?.()
      } catch (error) {
        closePrintWindow(printWindow)
        onError?.(error)
      }
    }, delay)
  }
}
