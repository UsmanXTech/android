package com.example.localjupyter.data

import com.example.localjupyter.model.*
import java.util.UUID

object SampleNotebookRepository {

    fun getSamples(): List<Pair<String, Notebook>> {
        return listOf(
            "Welcome to LocalJupyter.ipynb" to createWelcomeNotebook(),
            "Scientific Computing (NumPy & Matplotlib).ipynb" to createScientificNotebook(),
            "Data Analysis with Pandas.ipynb" to createPandasNotebook()
        )
    }

    private fun createWelcomeNotebook(): Notebook {
        val cells = listOf(
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.MARKDOWN,
                source = """
                    # 🪐 Welcome to LocalJupyter
                    
                    **LocalJupyter** is a standalone, ad-free, local Python notebook environment for Android.
                    
                    ### Key Features:
                    * **Persistent Python Runtime:** Variables and functions survive across cells.
                    * **Scientific Computing:** Built-in **NumPy**, **Matplotlib**, and **pandas**.
                    * **Genuine Jupyter Format:** Reads and saves official `.ipynb` files via Storage Access Framework.
                    * **Rich Output:** Supports text, HTML tables, Base64 PNG plots, and formatted tracebacks.
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # Variables created here will persist in memory
                    greeting = "Hello from LocalJupyter!"
                    a = 15
                    b = 27
                    print(greeting)
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # Using variables 'a' and 'b' defined in the previous cell
                    total = a + b
                    print("Calculation result:")
                    total
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # List comprehensions and mathematical functions
                    squares = [x**2 for x in range(1, 11)]
                    print("Squares from 1 to 10:", squares)
                    print("Sum of squares:")
                    sum(squares)
                """.trimIndent()
            )
        )
        return Notebook(cells = cells)
    }

    private fun createScientificNotebook(): Notebook {
        val cells = listOf(
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.MARKDOWN,
                source = """
                    # 🔬 Scientific Computing on Android
                    
                    This notebook demonstrates **NumPy** array operations and **Matplotlib** plotting.
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    import numpy as np

                    x = np.arange(1, 6)
                    print("Original array x:", x)
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # Vector power operation across cells
                    y = x ** 2
                    y
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    import numpy as np
                    import matplotlib.pyplot as plt

                    x = np.linspace(0, 10, 200)
                    y = np.sin(x)

                    plt.plot(x, y, label="sin(x)")
                    plt.title("Sine wave")
                    plt.xlabel("x")
                    plt.ylabel("sin(x)")
                    plt.grid(True)
                    plt.legend()
                    plt.show()
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # Multi-series plot with exponential damping
                    y2 = np.cos(x) * np.exp(-0.2 * x)

                    plt.plot(x, y2, label="cos(x) * exp(-0.2x)")
                    plt.title("Damped Oscillation")
                    plt.xlabel("Time (s)")
                    plt.ylabel("Amplitude")
                    plt.grid(True)
                    plt.legend()
                    plt.show()
                """.trimIndent()
            )
        )
        return Notebook(cells = cells)
    }

    private fun createPandasNotebook(): Notebook {
        val cells = listOf(
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.MARKDOWN,
                source = """
                    # 📊 Data Analysis with Pandas
                    
                    Explore DataFrames, statistical summaries, and rich HTML tables.
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    import pandas as pd

                    data = {
                        "Product": ["Laptop Pro", "4K Monitor", "Mechanical Keyboard", "Wireless Mouse", "Noise-Cancelling Audio"],
                        "Category": ["Computing", "Display", "Peripherals", "Peripherals", "Audio"],
                        "Price": [1299.99, 399.50, 129.00, 59.99, 249.00],
                        "Units_Sold": [55, 130, 420, 680, 210]
                    }
                    df = pd.DataFrame(data)
                    df
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    # Summary statistics table
                    df.describe()
                """.trimIndent()
            ),
            Cell(
                id = UUID.randomUUID().toString(),
                cellType = CellType.CODE,
                source = """
                    import matplotlib.pyplot as plt

                    # Visualize total revenue per product
                    revenue = [1299.99 * 55, 399.50 * 130, 129.00 * 420, 59.99 * 680, 249.00 * 210]
                    names = ["Laptop", "Monitor", "Keyboard", "Mouse", "Audio"]

                    plt.bar(names, revenue)
                    plt.title("Total Product Revenue ($)")
                    plt.xlabel("Product Line")
                    plt.ylabel("Revenue")
                    plt.grid(True)
                    plt.show()
                """.trimIndent()
            )
        )
        return Notebook(cells = cells)
    }
}
